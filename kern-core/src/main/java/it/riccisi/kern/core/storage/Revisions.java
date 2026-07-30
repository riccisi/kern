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
) implements it.riccisi.kern.api.append.AppendConditionState {
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

    @Override
    public SubjectRevision subjectRevision(final Subject subject) {
        return subjects.getOrDefault(Objects.requireNonNull(subject, "subject must not be null"), new SubjectRevision(0));
    }

    @Override
    public long consistencyRevision(final ConsistencyKey key) {
        return consistencyKeys.getOrDefault(Objects.requireNonNull(key, "consistency key must not be null"), 0L);
    }
}
