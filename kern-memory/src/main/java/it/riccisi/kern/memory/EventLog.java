package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StaleTailException;
import it.riccisi.kern.StoredEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cactoos.iterable.Filtered;
import org.cactoos.iterable.HeadOf;
import org.cactoos.list.ListOf;
import org.cactoos.scalar.LengthOf;
import org.cactoos.scalar.Unchecked;

/**
 * Namespace-local in-memory event history.
 *
 * <p>This object owns the ordered stored events for one namespace. It can expose
 * bounded filtered views of that history, append validated incoming events, and
 * calculate safe upper boundaries for subscription result windows. It does not
 * create {@link it.riccisi.kern.StoredEvents} observations; that coordination
 * belongs to {@link MemoryNamespace}.</p>
 */
final class EventLog {

    private final List<StoredEvent> events;
    private final MemoryEventSelection selection;

    EventLog() {
        this.events = new ArrayList<>();
        this.selection = new MemoryEventSelection();
    }

    /**
     * Current upper position of this namespace history.
     *
     * @return The last stored event position, or {@link Position#beginning()}
     *     when the history is empty.
     */
    Position head() {
        final Position head;
        if (this.events.isEmpty()) {
            head = Position.beginning();
        } else {
            head = this.events.getLast().position();
        }
        return head;
    }

    /**
     * Events matching a filter inside a bounded position range.
     *
     * <p>The range follows Kern observation semantics:
     * {@code after < position <= watermark}.</p>
     *
     * @param filter The semantic event filter.
     * @param after The exclusive lower boundary.
     * @param watermark The inclusive upper boundary.
     * @return Matching stored events in persisted order.
     */
    Iterable<StoredEvent> observed(
        final EventFilter filter,
        final Position after,
        final Position watermark
    ) {
        return new Filtered<>(
            event -> event.position().within(after, watermark)
                && this.selection.matches(filter, event),
            this.events
        );
    }

    /**
     * Limited events matching a filter inside a bounded position range.
     *
     * @param filter The semantic event filter.
     * @param after The exclusive lower boundary.
     * @param watermark The inclusive upper boundary.
     * @param count Maximum number of events to return.
     * @return Matching stored events in persisted order, bounded by count.
     */
    List<StoredEvent> observed(
        final EventFilter filter,
        final Position after,
        final Position watermark,
        final int count
    ) {
        return new ListOf<>(
            new HeadOf<>(
                count, this.observed(filter, after, watermark)
            )
        );
    }

    /**
     * Whether a bounded filtered range has at least one observable event.
     *
     * @param filter The semantic event filter.
     * @param after The exclusive lower boundary.
     * @param watermark The inclusive upper boundary.
     * @param count Maximum number of events requested.
     * @return True when at least one event can be observed.
     */
    boolean hasObservation(
        final EventFilter filter,
        final Position after,
        final Position watermark,
        final int count
    ) {
        return new Unchecked<>(
            new LengthOf(
                new HeadOf<>(
                    count,
                    this.observed(filter, after, watermark)
                )
            )
        ).value() > 0L;
    }

    /**
     * Appends a logical batch against a consistency boundary.
     *
     * <p>Incoming events are first validated for duplicate {@code EventId}s.
     * Idempotent retries are accepted, partial duplicates are rejected, and tail
     * staleness is checked only for genuinely missing events.</p>
     *
     * @param filter The filter defining the tail consistency boundary.
     * @param watermark The tail watermark.
     * @param events The incoming append batch.
     */
    void append(
        final EventFilter filter,
        final Position watermark,
        final Iterable<? extends Event> events
    ) {
        final IncomingEvents incoming = new IncomingEvents(events);
        final List<Event> missing = new ArrayList<>();
        for (final Event event : incoming) {
            final Optional<StoredEvent> existing = this.event(event);
            if (existing.isPresent()) {
                new EventIdentity(existing.get()).verify(event);
            } else {
                missing.add(event);
            }
        }

        if (missing.isEmpty()) {
            return;
        }
        if (missing.size() != incoming.size()) {
            throw new IllegalArgumentException(
                "Append must not mix new and already stored EventIds"
            );
        }

        this.appendMissing(filter, watermark, incoming, missing);
    }

    /**
     * Upper boundary for a subscription result window.
     *
     * <p>When the requested count is reached, the boundary is the last delivered
     * event position so the next continuation cannot skip matching events. When
     * fewer events are available, the current head is a safe exhausted-window
     * boundary.</p>
     *
     * @param filter The semantic event filter.
     * @param after The exclusive lower boundary.
     * @param watermark The inclusive upper boundary.
     * @param count Maximum requested events.
     * @return The safe upper boundary for the returned observation.
     */
    Position watermark(
        final EventFilter filter,
        final Position after,
        final Position watermark,
        final int count
    ) {
        final List<StoredEvent> available = this.observed(
            filter,
            after,
            watermark,
            count
        );
        final Position boundary;
        if (available.size() == count) {
            boundary = available.getLast().position();
        } else {
            boundary = this.head();
        }
        return boundary;
    }

    private void appendMissing(
        final EventFilter filter,
        final Position watermark,
        final IncomingEvents incoming,
        final List<Event> missing
    ) {
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

}
