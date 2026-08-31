package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.TagName;
import org.cactoos.BiFunc;
import org.cactoos.Func;
import org.cactoos.iterable.Filtered;
import org.cactoos.iterable.TailOf;
import org.cactoos.scalar.Folded;

/**
 * In-memory interpreter for semantic event reduction descriptions.
 */
final class MemoryReductionSelection
    implements EventReductionSelection<Func<Iterable<StoredEvent>, Iterable<StoredEvent>>> {

    @Override
    public Func<Iterable<StoredEvent>, Iterable<StoredEvent>> latest() {
        return events -> new TailOf<>(1, events);
    }

    @Override
    public Func<Iterable<StoredEvent>, Iterable<StoredEvent>> latestBy(final TagName tag) {
        return events -> new LatestEventsByTag(events, tag);
    }

    @Override
    public Func<Iterable<StoredEvent>, Iterable<StoredEvent>> matching(final EventFilter filter) {
        return events -> new Filtered<>(
            event -> new MemoryEventSelection().matches(filter, event),
            events
        );
    }

    @Override
    public Func<Iterable<StoredEvent>, Iterable<StoredEvent>> excluding(final EventFilter filter) {
        return events -> new Filtered<>(
            event -> !new MemoryEventSelection().matches(filter, event),
            events
        );
    }
}
