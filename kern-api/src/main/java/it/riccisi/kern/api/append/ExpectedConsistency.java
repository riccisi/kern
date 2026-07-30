package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.error.ConsistencyConflict;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ExpectedConsistency(Map<ConsistencyKey, Long> revisions) implements AppendCondition {
    public ExpectedConsistency {
        revisions = Map.copyOf(Objects.requireNonNull(revisions, "consistency revisions must not be null"));
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("consistency revisions must not be empty");
        }
        for (Long revision : revisions.values()) {
            if (revision < 0) {
                throw new IllegalArgumentException("consistency revision must not be negative");
            }
        }
    }

    @Override
    public Set<Subject> observedSubjects() {
        return Set.of();
    }

    @Override
    public Set<ConsistencyKey> observedConsistencyKeys() {
        return revisions.keySet();
    }

    @Override
    public void verify(final AppendConditionState state, final String diagnosticId) {
        Objects.requireNonNull(state, "append condition state must not be null");
        Objects.requireNonNull(diagnosticId, "diagnostic id must not be null");
        for (Map.Entry<ConsistencyKey, Long> expectation : revisions.entrySet()) {
            long actual = state.consistencyRevision(expectation.getKey());
            if (actual != expectation.getValue()) {
                throw new ConsistencyConflict(diagnosticId, expectation.getKey(), expectation.getValue(), actual);
            }
        }
    }
}
