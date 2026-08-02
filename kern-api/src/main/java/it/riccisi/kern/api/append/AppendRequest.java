package it.riccisi.kern.api.append;

import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import java.util.List;
import java.util.Objects;

/**
 * Logical append request evaluated by one query-based condition.
 */
public record AppendRequest(
    Namespace namespace,
    List<EventData> events,
    AppendCondition condition,
    IdempotencyKey idempotencyKey
) {
    public AppendRequest {
        Objects.requireNonNull(namespace, "namespace must not be null");
        events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        Objects.requireNonNull(condition, "append condition must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
    }
}
