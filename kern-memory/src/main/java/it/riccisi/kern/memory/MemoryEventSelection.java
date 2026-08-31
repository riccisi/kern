package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventSelection;
import it.riccisi.kern.EventType;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tag;
import org.cactoos.Func;
import org.cactoos.func.UncheckedFunc;

/**
 * In-memory interpreter for semantic {@link EventFilter} descriptions.
 */
final class MemoryEventSelection implements EventSelection<Func<StoredEvent, Boolean>> {

    boolean matches(final EventFilter filter, final StoredEvent event) {
        return new UncheckedFunc<>(filter.describe(this)).apply(event);
    }

    @Override
    public Func<StoredEvent, Boolean> all(
        final Iterable<? extends Func<StoredEvent, Boolean>> selections
    ) {
        return new AllMatches(selections);
    }

    @Override
    public Func<StoredEvent, Boolean> any(
        final Iterable<? extends Func<StoredEvent, Boolean>> selections
    ) {
        return new AnyMatches(selections);
    }

    @Override
    public Func<StoredEvent, Boolean> typedBy(final EventType type) {
        return event -> event.type().equals(type);
    }

    @Override
    public Func<StoredEvent, Boolean> taggedAs(final Tag tag) {
        return event -> new TagValueOf(event, tag.name())
            .value()
            .filter(value -> value.equals(tag.value()))
            .isPresent();
    }
}
