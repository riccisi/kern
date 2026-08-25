package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StaleTailException;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.NonNull;

final class MemoryNamespace {

    private final List<StoredEvent> events;
    private final List<MemoryWaiter> waiters;
    private final MemoryEventSelection selection;

    MemoryNamespace() {
        this.events = new ArrayList<>();
        this.waiters = new ArrayList<>();
        this.selection = new MemoryEventSelection();
    }

    synchronized StoredEvents observe(
        @NonNull final EventFilter filter,
        @NonNull final Position after
    ) {
        return new MemoryObservation(
            this,
            filter,
            after,
            this.head(),
            List.of()
        );
    }

    synchronized List<StoredEvent> events(
        final EventFilter filter,
        final Position after,
        final Position watermark,
        final int limit
    ) {
        final List<StoredEvent> observed = new ArrayList<>();
        for (final StoredEvent event : this.events) {
            if (
                event.position().compareTo(after) > 0
                    && event.position().compareTo(watermark) <= 0
                    && this.selection.matches(filter, event)
            ) {
                observed.add(event);
                if (observed.size() == limit) {
                    break;
                }
            }
        }
        return observed;
    }

    synchronized void append(
        final EventFilter filter,
        final Position watermark,
        final Iterable<? extends Event> events
    ) {
        final MemoryBatch incoming = new MemoryBatch(events);
        final List<Event> missing = new ArrayList<>();
        for (final Event event : incoming) {
            final Optional<StoredEvent> existing = this.event(event);
            if (existing.isPresent()) {
                new MemoryEventIdentity(existing.get()).verify(event);
            } else {
                missing.add(event);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (missing.size() != incoming.size()) {
            throw new IllegalArgumentException("Append must not mix new and already stored EventIds");
        }
        this.conflict(filter, watermark).ifPresent(
            event -> {
                throw new StaleTailException(new MemoryConflict(event));
            }
        );
        for (final Event event : missing) {
            this.events.add(
                new MemoryStoredEvent(
                    event,
                    new Position(this.head().longValue() + 1L),
                    Instant.now()
                )
            );
        }
        this.completeWaiters();
    }

    synchronized CompletionStage<StoredEvents> next(
        final EventFilter filter,
        final Position watermark,
        final int count
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException("Subscription demand must be positive");
        }
        final CompletableFuture<StoredEvents> stage = new CompletableFuture<>();
        final List<StoredEvent> available = this.events(filter, watermark, this.head(), count);
        if (available.isEmpty()) {
            this.waiters.add(new MemoryWaiter(filter, watermark, count, stage));
        } else {
            stage.complete(this.window(filter, watermark, count, available));
        }
        return stage;
    }

    synchronized Position head() {
        final Position head;
        if (this.events.isEmpty()) {
            head = Position.beginning();
        } else {
            head = this.events.get(this.events.size() - 1).position();
        }
        return head;
    }

    private Optional<StoredEvent> conflict(
        final EventFilter filter,
        final Position watermark
    ) {
        return this.events.stream()
            .filter(event -> event.position().compareTo(watermark) > 0)
            .filter(event -> this.selection.matches(filter, event))
            .findFirst();
    }

    private Optional<StoredEvent> event(final Event event) {
        return this.events.stream()
            .filter(stored -> stored.id().equals(event.id()))
            .findFirst();
    }

    synchronized StoredEvents window(
        final EventFilter filter,
        final Position after,
        final int count,
        final List<StoredEvent> available
    ) {
        final Position watermark;
        if (available.size() == count) {
            watermark = available.get(available.size() - 1).position();
        } else {
            watermark = this.head();
        }
        return new MemoryObservation(this, filter, after, watermark, List.of());
    }

    private void completeWaiters() {
        final Iterator<MemoryWaiter> iterator = this.waiters.iterator();
        while (iterator.hasNext()) {
            final MemoryWaiter waiter = iterator.next();
            if (waiter.completeFrom(this)) {
                iterator.remove();
            }
        }
    }
}
