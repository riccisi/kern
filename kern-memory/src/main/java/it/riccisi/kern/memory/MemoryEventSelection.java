package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventSelection;
import it.riccisi.kern.EventType;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tag;
import java.util.function.Predicate;

final class MemoryEventSelection implements EventSelection<Predicate<StoredEvent>> {

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
        return event -> new MemoryTagValue(event, tag.name())
            .value()
            .filter(value -> value.equals(tag.value()))
            .isPresent();
    }
}
