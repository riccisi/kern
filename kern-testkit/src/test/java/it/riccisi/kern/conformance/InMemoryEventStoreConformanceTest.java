package it.riccisi.kern.conformance;

import it.riccisi.kern.Conflict;
import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReduction;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.EventSelection;
import it.riccisi.kern.EventStore;
import it.riccisi.kern.EventType;
import it.riccisi.kern.Namespace;
import it.riccisi.kern.Position;
import it.riccisi.kern.StaleTailException;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.Subscription;
import it.riccisi.kern.Tag;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import it.riccisi.kern.Tail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

final class InMemoryEventStoreConformanceTest extends SemanticEventStoreConformanceTest {

    @Override
    protected EventStore store() {
        return new InMemoryEventStore();
    }

    private static final class InMemoryEventStore implements EventStore {

        private final Map<Namespace, List<StoredEvent>> events;
        private final List<Waiter> waiters;

        InMemoryEventStore() {
            this.events = new LinkedHashMap<>();
            this.waiters = new ArrayList<>();
        }

        @Override
        public synchronized StoredEvents events(
            final Namespace namespace,
            final EventFilter filter,
            final Position after
        ) {
            return new InMemoryStoredEvents(
                this,
                namespace,
                filter,
                after,
                this.head(namespace),
                List.of()
            );
        }

        synchronized void append(
            final Namespace namespace,
            final EventFilter filter,
            final Position watermark,
            final Iterable<? extends Event> events
        ) {
            this.conflict(namespace, filter, watermark).ifPresent(
                event -> {
                    throw new StaleTailException(new InMemoryConflict(event));
                }
            );
            for (final Event event : events) {
                this.events.computeIfAbsent(namespace, ignored -> new ArrayList<>()).add(
                    new InMemoryStoredEvent(
                        event,
                        new Position(this.head(namespace).value() + 1L),
                        Instant.now()
                    )
                );
            }
            this.completeWaiters();
        }

        synchronized List<StoredEvent> observed(
            final Namespace namespace,
            final EventFilter filter,
            final Position after,
            final Position watermark,
            final int limit
        ) {
            final List<StoredEvent> observed = new ArrayList<>();
            for (final StoredEvent event : this.events.getOrDefault(namespace, List.of())) {
                if (
                    event.position().compareTo(after) > 0
                        && event.position().compareTo(watermark) <= 0
                        && new InMemoryEventSelection().matches(filter, event)
                ) {
                    observed.add(event);
                    if (observed.size() == limit) {
                        break;
                    }
                }
            }
            return observed;
        }

        synchronized CompletionStage<StoredEvents> next(
            final Namespace namespace,
            final EventFilter filter,
            final Position watermark,
            final int count
        ) {
            if (count <= 0) {
                throw new IllegalArgumentException("Subscription demand must be positive");
            }
            final List<StoredEvent> available = this.observed(namespace, filter, watermark, this.head(namespace), count);
            final CompletableFuture<StoredEvents> stage = new CompletableFuture<>();
            if (available.isEmpty()) {
                this.waiters.add(new Waiter(namespace, filter, watermark, count, stage));
            } else {
                stage.complete(this.window(namespace, filter, watermark, count, available));
            }
            return stage;
        }

        private Position head(final Namespace namespace) {
            final List<StoredEvent> stored = this.events.getOrDefault(namespace, List.of());
            final Position head;
            if (stored.isEmpty()) {
                head = Position.beginning();
            } else {
                head = stored.get(stored.size() - 1).position();
            }
            return head;
        }

        private Optional<StoredEvent> conflict(
            final Namespace namespace,
            final EventFilter filter,
            final Position watermark
        ) {
            return this.events.getOrDefault(namespace, List.of()).stream()
                .filter(event -> event.position().compareTo(watermark) > 0)
                .filter(event -> new InMemoryEventSelection().matches(filter, event))
                .findFirst();
        }

        private StoredEvents window(
            final Namespace namespace,
            final EventFilter filter,
            final Position after,
            final int count,
            final List<StoredEvent> available
        ) {
            final Position watermark;
            if (available.size() == count) {
                watermark = available.get(available.size() - 1).position();
            } else {
                watermark = this.head(namespace);
            }
            return new InMemoryStoredEvents(this, namespace, filter, after, watermark, List.of());
        }

        private void completeWaiters() {
            final Iterator<Waiter> iterator = this.waiters.iterator();
            while (iterator.hasNext()) {
                final Waiter waiter = iterator.next();
                final List<StoredEvent> available = this.observed(
                    waiter.namespace,
                    waiter.filter,
                    waiter.watermark,
                    this.head(waiter.namespace),
                    waiter.count
                );
                if (!available.isEmpty()) {
                    iterator.remove();
                    waiter.stage.complete(
                        this.window(waiter.namespace, waiter.filter, waiter.watermark, waiter.count, available)
                    );
                }
            }
        }
    }

    private static final class InMemoryStoredEvents implements StoredEvents {

        private final InMemoryEventStore store;
        private final Namespace namespace;
        private final EventFilter filter;
        private final Position after;
        private final Position watermark;
        private final List<EventReduction> reductions;

        InMemoryStoredEvents(
            final InMemoryEventStore store,
            final Namespace namespace,
            final EventFilter filter,
            final Position after,
            final Position watermark,
            final List<EventReduction> reductions
        ) {
            this.store = store;
            this.namespace = namespace;
            this.filter = filter;
            this.after = after;
            this.watermark = watermark;
            this.reductions = List.copyOf(reductions);
        }

        @Override
        public StoredEvents reduce(final EventReduction reduction) {
            final List<EventReduction> next = new ArrayList<>(this.reductions);
            next.add(reduction);
            return new InMemoryStoredEvents(
                this.store,
                this.namespace,
                this.filter,
                this.after,
                this.watermark,
                next
            );
        }

        @Override
        public Tail tail() {
            return events -> this.store.append(this.namespace, this.filter, this.watermark, events);
        }

        @Override
        public Subscription follow() {
            return count -> this.store.next(this.namespace, this.filter, this.watermark, count);
        }

        @Override
        public Iterator<StoredEvent> iterator() {
            List<StoredEvent> observed = this.store.observed(
                this.namespace,
                this.filter,
                this.after,
                this.watermark,
                Integer.MAX_VALUE
            );
            final InMemoryReductionSelection selection = new InMemoryReductionSelection();
            for (final EventReduction reduction : this.reductions) {
                observed = reduction.describe(selection).apply(observed);
            }
            return observed.iterator();
        }
    }

    private static final class InMemoryReductionSelection
        implements EventReductionSelection<UnaryOperator<List<StoredEvent>>> {

        @Override
        public UnaryOperator<List<StoredEvent>> latest() {
            return events -> {
                final List<StoredEvent> reduced;
                if (events.isEmpty()) {
                    reduced = List.of();
                } else {
                    reduced = List.of(events.get(events.size() - 1));
                }
                return reduced;
            };
        }

        @Override
        public UnaryOperator<List<StoredEvent>> latestBy(final TagName tag) {
            return events -> {
                final Map<TagValue, StoredEvent> latest = new LinkedHashMap<>();
                for (final StoredEvent event : events) {
                    new TagValueOf(event, tag).value().ifPresent(value -> latest.put(value, event));
                }
                return latest.values().stream()
                    .sorted(Comparator.comparing(StoredEvent::position))
                    .toList();
            };
        }

        @Override
        public UnaryOperator<List<StoredEvent>> matching(final EventFilter filter) {
            return events -> events.stream()
                .filter(event -> new InMemoryEventSelection().matches(filter, event))
                .toList();
        }

        @Override
        public UnaryOperator<List<StoredEvent>> excluding(final EventFilter filter) {
            return events -> events.stream()
                .filter(Predicate.not(event -> new InMemoryEventSelection().matches(filter, event)))
                .toList();
        }
    }

    private static final class InMemoryEventSelection implements EventSelection<Predicate<StoredEvent>> {

        boolean matches(final EventFilter filter, final StoredEvent event) {
            return filter.describe(this).test(event);
        }

        @Override
        public Predicate<StoredEvent> all(final Iterable<? extends Predicate<StoredEvent>> selections) {
            return event -> {
                boolean matches = true;
                for (final Predicate<StoredEvent> selection : selections) {
                    matches = matches && selection.test(event);
                }
                return matches;
            };
        }

        @Override
        public Predicate<StoredEvent> any(final Iterable<? extends Predicate<StoredEvent>> selections) {
            return event -> {
                boolean matches = false;
                for (final Predicate<StoredEvent> selection : selections) {
                    matches = matches || selection.test(event);
                }
                return matches;
            };
        }

        @Override
        public Predicate<StoredEvent> typedBy(final EventType type) {
            return event -> event.type().equals(type);
        }

        @Override
        public Predicate<StoredEvent> taggedAs(final Tag tag) {
            return event -> new TagValueOf(event, tag.name())
                .value()
                .filter(value -> value.equals(tag.value()))
                .isPresent();
        }
    }

    private record TagValueOf(StoredEvent event, TagName name) {

        Optional<TagValue> value() {
            Optional<TagValue> value = Optional.empty();
            for (final Tag tag : this.event.tags()) {
                if (tag.name().equals(this.name)) {
                    value = Optional.of(tag.value());
                    break;
                }
            }
            return value;
        }
    }

    private record InMemoryStoredEvent(Event event, Position position, Instant storedAt) implements StoredEvent {

        @Override
        public it.riccisi.kern.EventId id() {
            return this.event.id();
        }

        @Override
        public EventType type() {
            return this.event.type();
        }

        @Override
        public it.riccisi.kern.Tags tags() {
            return this.event.tags();
        }

        @Override
        public it.riccisi.kern.Data data() {
            return this.event.data();
        }
    }

    private record InMemoryConflict(StoredEvent event) implements Conflict {
    }

    private static final class Waiter {

        private final Namespace namespace;
        private final EventFilter filter;
        private final Position watermark;
        private final int count;
        private final CompletableFuture<StoredEvents> stage;

        Waiter(
            final Namespace namespace,
            final EventFilter filter,
            final Position watermark,
            final int count,
            final CompletableFuture<StoredEvents> stage
        ) {
            this.namespace = namespace;
            this.filter = filter;
            this.watermark = watermark;
            this.count = count;
            this.stage = stage;
        }
    }
}
