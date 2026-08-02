package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import java.time.Instant;
import java.util.Objects;

/**
 * Storage-local event with namespace and global sequence position.
 */
public record StoredEvent(
    Namespace namespace,
    SequencePosition position,
    EventData data,
    Instant recordedAt
) {
    public StoredEvent {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(data, "event data must not be null");
        Objects.requireNonNull(recordedAt, "recorded at must not be null");
    }
}
