package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
import java.util.Objects;
import java.util.Set;

public record ExpectedSubjectRevision(Subject subject, SubjectRevision revision) implements AppendCondition {
    public ExpectedSubjectRevision {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(revision, "subject revision must not be null");
        if (revision.value() == 0) {
            throw new IllegalArgumentException("expected subject revision must be positive");
        }
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
        if (!revision.equals(actual)) {
            throw new SubjectRevisionConflict(diagnosticId, subject, revision, actual);
        }
    }
}
