package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AppendRequest(
    Namespace namespace,
    IdempotencyKey idempotencyKey,
    List<EventData> events,
    AppendCondition condition,
    Set<ConsistencyKey> touchedConsistencyKeys,
    Durability durability
) {
    public AppendRequest {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        Objects.requireNonNull(condition, "append condition must not be null");
        touchedConsistencyKeys = Set.copyOf(
            Objects.requireNonNull(touchedConsistencyKeys, "touched consistency keys must not be null")
        );
        Objects.requireNonNull(durability, "durability must not be null");
    }
}
