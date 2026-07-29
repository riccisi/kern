package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EventQuery(
    Namespace namespace,
    SubjectFilter subject,
    Set<EventType> types,
    Map<String, String> exactTags,
    Position afterPosition,
    int limit,
    Direction direction
) {
    public EventQuery {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(subject, "subject filter must not be null");
        types = Set.copyOf(Objects.requireNonNull(types, "event types must not be null"));
        exactTags = Map.copyOf(Objects.requireNonNull(exactTags, "exact tags must not be null"));
        Objects.requireNonNull(afterPosition, "after position must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
