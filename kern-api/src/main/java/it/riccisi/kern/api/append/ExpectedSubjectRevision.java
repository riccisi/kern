package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.Objects;

public record ExpectedSubjectRevision(Subject subject, SubjectRevision revision) implements AppendCondition {
    public ExpectedSubjectRevision {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(revision, "subject revision must not be null");
        if (revision.value() == 0) {
            throw new IllegalArgumentException("expected subject revision must be positive");
        }
    }
}
