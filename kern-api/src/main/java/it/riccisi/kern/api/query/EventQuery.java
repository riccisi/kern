package it.riccisi.kern.api.query;

import it.riccisi.kern.api.event.EventData;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Query whose items are OR-combined while each item keeps AND semantics.
 */
public record EventQuery(List<QueryItem> items) {
    public EventQuery {
        items = List.copyOf(Objects.requireNonNull(items, "query items must not be null"));
    }

    public boolean matches(final EventData event) {
        return matchingItem(event).isPresent();
    }

    public OptionalInt matchingItem(final EventData event) {
        Objects.requireNonNull(event, "event data must not be null");
        if (items.isEmpty()) {
            return OptionalInt.of(0);
        }
        return IntStream.range(0, items.size())
            .filter(index -> items.get(index).matches(event))
            .findFirst();
    }
}
