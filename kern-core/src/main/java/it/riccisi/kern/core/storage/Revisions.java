package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.Map;
import java.util.Objects;

public record Revisions(
    Map<Subject, SubjectRevision> subjects,
    Map<ConsistencyKey, Long> consistencyKeys,
    Position observedAtPosition
) {
    public Revisions {
        subjects = Map.copyOf(Objects.requireNonNull(subjects, "subject revisions must not be null"));
        consistencyKeys = Map.copyOf(Objects.requireNonNull(consistencyKeys, "consistency revisions must not be null"));
        for (Long revision : consistencyKeys.values()) {
            if (revision < 0) {
                throw new IllegalArgumentException("consistency revision must not be negative");
            }
        }
        Objects.requireNonNull(observedAtPosition, "observed position must not be null");
    }
}
