package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Subject;
import java.util.Objects;
import java.util.Set;

public record AnyAppend() implements AppendCondition {
    @Override
    public Set<Subject> observedSubjects() {
        return Set.of();
    }

    @Override
    public Set<ConsistencyKey> observedConsistencyKeys() {
        return Set.of();
    }

    @Override
    public void verify(final AppendConditionState state, final String diagnosticId) {
        Objects.requireNonNull(state, "append condition state must not be null");
        Objects.requireNonNull(diagnosticId, "diagnostic id must not be null");
    }
}
