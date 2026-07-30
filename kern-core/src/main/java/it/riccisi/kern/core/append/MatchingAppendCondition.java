package it.riccisi.kern.core.append;

import it.riccisi.kern.api.append.AnyAppend;
import it.riccisi.kern.api.append.AppendCondition;
import it.riccisi.kern.api.append.ExpectedConsistency;
import it.riccisi.kern.api.append.ExpectedSubjectRevision;
import it.riccisi.kern.api.append.NoSubject;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.Revisions;
import java.util.Objects;

final class MatchingAppendCondition {
    private final AppendCondition condition;
    private final Revisions revisions;
    private final String diagnosticId;

    MatchingAppendCondition(
        final AppendCondition condition,
        final Revisions revisions,
        final String diagnosticId
    ) {
        this.condition = Objects.requireNonNull(condition, "append condition must not be null");
        this.revisions = Objects.requireNonNull(revisions, "revisions must not be null");
        this.diagnosticId = Objects.requireNonNull(diagnosticId, "diagnostic id must not be null");
        if (diagnosticId.isBlank()) {
            throw new IllegalArgumentException("diagnostic id must not be blank");
        }
    }

    void verify() {
        switch (condition) {
            case AnyAppend ignored -> {
            }
            case NoSubject expected -> verifyNoSubject(expected);
            case ExpectedSubjectRevision expected -> verifyExpectedSubject(expected);
            case ExpectedConsistency ignored -> throw new UnsupportedOperationException(
                "expected consistency is implemented by KERN-017"
            );
        }
    }

    private void verifyNoSubject(final NoSubject expected) {
        SubjectRevision actual = revisions.subjects().getOrDefault(expected.subject(), new SubjectRevision(0));
        if (actual.value() != 0) {
            throw new SubjectRevisionConflict(
                diagnosticId,
                expected.subject(),
                new SubjectRevision(0),
                actual
            );
        }
    }

    private void verifyExpectedSubject(final ExpectedSubjectRevision expected) {
        SubjectRevision actual = revisions.subjects().getOrDefault(expected.subject(), new SubjectRevision(0));
        if (!actual.equals(expected.revision())) {
            throw new SubjectRevisionConflict(
                diagnosticId,
                expected.subject(),
                expected.revision(),
                actual
            );
        }
    }
}
