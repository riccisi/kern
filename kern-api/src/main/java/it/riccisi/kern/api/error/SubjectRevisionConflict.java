package it.riccisi.kern.api.error;

import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.Objects;

public final class SubjectRevisionConflict extends EventStoreException {
    private final Subject subject;
    private final SubjectRevision expected;
    private final SubjectRevision actual;

    public SubjectRevisionConflict(
        String diagnosticId,
        Subject subject,
        SubjectRevision expected,
        SubjectRevision actual
    ) {
        super(diagnosticId, "subject revision does not match expected revision");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.expected = Objects.requireNonNull(expected, "expected revision must not be null");
        this.actual = Objects.requireNonNull(actual, "actual revision must not be null");
    }

    public Subject subject() {
        return subject;
    }

    public SubjectRevision expected() {
        return expected;
    }

    public SubjectRevision actual() {
        return actual;
    }
}
