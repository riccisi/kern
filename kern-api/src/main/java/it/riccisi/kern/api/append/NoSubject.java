package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
import java.util.Objects;
import java.util.Set;

public record NoSubject(Subject subject) implements AppendCondition {
    public NoSubject {
        Objects.requireNonNull(subject, "subject must not be null");
    }

    @Override
    public Set<Subject> observedSubjects() {
        return Set.of(subject);
    }

    @Override
    public Set<ConsistencyKey> observedConsistencyKeys() {
        return Set.of();
    }

    @Override
    public void verify(final AppendConditionState state, final String diagnosticId) {
        Objects.requireNonNull(state, "append condition state must not be null");
        Objects.requireNonNull(diagnosticId, "diagnostic id must not be null");
        SubjectRevision actual = state.subjectRevision(subject);
        if (actual.value() != 0) {
            throw new SubjectRevisionConflict(diagnosticId, subject, new SubjectRevision(0), actual);
        }
    }
}
