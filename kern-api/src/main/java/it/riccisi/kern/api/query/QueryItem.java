package it.riccisi.kern.api.query;

import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import java.util.Objects;
import java.util.Set;

/**
 * One conjunctive query branch: event type constraint and required tags.
 */
public record QueryItem(
    Set<EventType> types,
    Set<EventTag> tags
) {
    public QueryItem {
        types = Set.copyOf(Objects.requireNonNull(types, "query item types must not be null"));
        tags = Set.copyOf(Objects.requireNonNull(tags, "query item tags must not be null"));
    }

    public boolean matches(final EventData event) {
        Objects.requireNonNull(event, "event data must not be null");
        return (types.isEmpty() || types.contains(event.type()))
            && event.tags().containsAll(tags);
    }
}
