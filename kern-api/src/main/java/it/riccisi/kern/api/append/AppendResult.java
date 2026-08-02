package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.Map;
import java.util.Objects;

public record AppendResult(
    Position fromPosition,
    Position toPosition,
    Map<Subject, SubjectRevision> subjectRevisions,
    Map<ConsistencyKey, ConsistencyRevision> consistencyRevisions,
    boolean replayedFromIdempotency
) {
    public AppendResult {
        Objects.requireNonNull(fromPosition, "from position must not be null");
        Objects.requireNonNull(toPosition, "to position must not be null");
        if (toPosition.value() < fromPosition.value()) {
            throw new IllegalArgumentException("to position must not be before from position");
        }
        subjectRevisions = Map.copyOf(Objects.requireNonNull(subjectRevisions, "subject revisions must not be null"));
        consistencyRevisions = Map.copyOf(
            Objects.requireNonNull(consistencyRevisions, "consistency revisions must not be null")
        );
    }
}
