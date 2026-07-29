package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SubjectRevision;
import java.time.Instant;
import java.util.Objects;

public record StoredEvent(
    Namespace namespace,
    Position position,
    SubjectRevision subjectRevision,
    EventData data,
    Instant recordedAt
) {
    public StoredEvent {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(subjectRevision, "subject revision must not be null");
        Objects.requireNonNull(data, "event data must not be null");
        Objects.requireNonNull(recordedAt, "recorded at must not be null");
    }
}
