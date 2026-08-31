package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.Data;
import it.riccisi.kern.EventId;
import it.riccisi.kern.EventType;
import it.riccisi.kern.Tags;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Ordered incoming append batch with unique event identities.
 *
 * <p>The object materializes the caller supplied events once, preserves their
 * relative order, and rejects duplicate {@link EventId}s before append
 * semantics such as idempotency and tail staleness are evaluated.</p>
 */
final class IncomingEvents implements Iterable<Event> {

    private final List<Event> events;

    IncomingEvents(@NonNull final Iterable<? extends Event> source) {
        final Set<Event> unique = new LinkedHashSet<>();
        for (final Event event : source) {
            if (!unique.add(new EventIdentifiedById(event))) {
                throw new IllegalArgumentException(
                    "Append batch must not contain duplicate EventIds"
                );
            }
        }
        this.events = List.copyOf(unique);
    }

    int size() {
        return this.events.size();
    }

    @Override
    public Iterator<Event> iterator() {
        return this.events.iterator();
    }

    /**
     * Event decorator whose Java identity is based only on {@link EventId}.
     */
    @RequiredArgsConstructor
    private static final class EventIdentifiedById implements Event {

        @NonNull private final Event event;

        @Override
        public EventId id() {
            return this.event.id();
        }

        @Override
        public EventType type() {
            return this.event.type();
        }

        @Override
        public Tags tags() {
            return this.event.tags();
        }

        @Override
        public Data data() {
            return this.event.data();
        }

        @Override
        public boolean equals(final Object other) {
            return this == other || other instanceof EventIdentifiedById that
                && this.event.id().equals(that.event.id());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.event.id());
        }
    }
}
