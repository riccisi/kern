package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

final class MemoryReductionSelection
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
                new MemoryTagValue(event, tag).value().ifPresent(value -> latest.put(value, event));
            }
            return latest.values().stream()
                .sorted(Comparator.comparing(StoredEvent::position))
                .toList();
        };
    }

    @Override
    public UnaryOperator<List<StoredEvent>> matching(final EventFilter filter) {
        return events -> events.stream()
            .filter(event -> new MemoryEventSelection().matches(filter, event))
            .toList();
    }

    @Override
    public UnaryOperator<List<StoredEvent>> excluding(final EventFilter filter) {
        return events -> events.stream()
            .filter(Predicate.not(event -> new MemoryEventSelection().matches(filter, event)))
            .toList();
    }
}
