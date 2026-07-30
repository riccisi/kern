package it.riccisi.kern.api.append;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.error.ConsistencyConflict;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.EventStore;
import it.riccisi.kern.api.error.EventStoreException;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class AppendContractsTest {
    @Test
    void buildsAppendRequestWithExplicitConditionAndDurability() {
        AppendRequest request = new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-course-c1-20260729"),
            List.of(enrollmentConfirmed()),
            new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(7)),
            Set.of(new ConsistencyKey("course:C1")),
            Durability.DURABLE
        );

        assertThat(request.condition()).isEqualTo(
            new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(7))
        );
    }

    @Test
    void rejectsAppendRequestWithoutEvents() {
        assertThatThrownBy(() -> new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-empty"),
            List.of(),
            new AnyAppend(),
            Set.of(),
            Durability.RELAXED
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("events must not be empty");
    }

    @Test
    void rejectsAppendRequestWithoutCondition() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AppendRequest(
                new Namespace("education"),
                new IdempotencyKey("append-null-condition"),
                List.of(enrollmentConfirmed()),
                null,
                Set.of(),
                Durability.DURABLE
            ))
            .withMessage("append condition must not be null");
    }

    @Test
    void rejectsZeroExpectedSubjectRevision() {
        assertThatThrownBy(() -> new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expected subject revision must be positive");
    }

    @Test
    void copiesAppendRequestCollections() {
        List<EventData> events = new java.util.ArrayList<>();
        events.add(enrollmentConfirmed());
        Set<ConsistencyKey> keys = new java.util.HashSet<>();
        keys.add(new ConsistencyKey("course:C1"));

        AppendRequest request = new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-copied"),
            events,
            new AnyAppend(),
            keys,
            Durability.DURABLE
        );
        events.clear();
        keys.clear();

        assertThat(request.events()).hasSize(1);
    }

    @Test
    void copiesEventPayloadAndMetadata() {
        byte[] payload = "payload-17".getBytes(StandardCharsets.UTF_8);
        byte[] metadata = "metadata-23".getBytes(StandardCharsets.UTF_8);
        EventData event = new EventData(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1")),
            new EventType("EnrollmentConfirmed.v1"),
            new Subject("course:C1"),
            new ContentType("application/json"),
            new SchemaReference("schema://education/enrollment-confirmed/1"),
            payload,
            metadata,
            Map.of("source", "registration")
        );
        payload[0] = 'X';
        event.metadata()[0] = 'X';

        assertThat(event.payload()).startsWith((byte) 'p');
    }

    @Test
    void comparesEventDataByByteContent() {
        assertThat(enrollmentConfirmed()).isEqualTo(enrollmentConfirmed());
    }

    @Test
    void rejectsEmptyConsistencyExpectation() {
        assertThatThrownBy(() -> new ExpectedConsistency(Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consistency revisions must not be empty");
    }

    @Test
    void rejectsNegativeConsistencyExpectation() {
        assertThatThrownBy(() -> new ExpectedConsistency(Map.of(new ConsistencyKey("course:C1"), -1L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consistency revision must not be negative");
    }

    @Test
    void letsEachConditionVerifyItsOwnExpectation() {
        AppendConditionState revisions = new AppendConditionState() {
            @Override
            public SubjectRevision subjectRevision(final Subject subject) {
                return new SubjectRevision(7);
            }

            @Override
            public long consistencyRevision(final ConsistencyKey key) {
                return 11L;
            }
        };

        new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(7))
            .verify(revisions, "diag-17");
        new ExpectedConsistency(Map.of(new ConsistencyKey("course:C1"), 11L))
            .verify(revisions, "diag-17");
    }

    @Test
    void reportsConsistencyConflictFromTheCondition() {
        AppendConditionState revisions = new AppendConditionState() {
            @Override
            public SubjectRevision subjectRevision(final Subject subject) {
                return new SubjectRevision(0);
            }

            @Override
            public long consistencyRevision(final ConsistencyKey key) {
                return 12L;
            }
        };

        assertThatThrownBy(() -> new ExpectedConsistency(Map.of(new ConsistencyKey("course:C1"), 11L))
            .verify(revisions, "diag-17"))
            .isInstanceOf(ConsistencyConflict.class)
            .hasMessage("consistency key revision does not match expected revision");
    }

    @Test
    void reportsSubjectConflictFromTheCondition() {
        AppendConditionState revisions = new AppendConditionState() {
            @Override
            public SubjectRevision subjectRevision(final Subject subject) {
                return new SubjectRevision(1);
            }

            @Override
            public long consistencyRevision(final ConsistencyKey key) {
                return 0L;
            }
        };

        assertThatThrownBy(() -> new NoSubject(new Subject("course:C1")).verify(revisions, "diag-17"))
            .isInstanceOf(SubjectRevisionConflict.class)
            .hasMessage("subject revision does not match expected revision");
    }

    @Test
    void identifiesAllRevisionsNeededForAnAppend() {
        AppendRequest request = new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-observed-revisions"),
            List.of(enrollmentConfirmed()),
            new ExpectedSubjectRevision(new Subject("student:S1"), new SubjectRevision(3)),
            Set.of(new ConsistencyKey("course:C1")),
            Durability.DURABLE
        );

        assertThat(request.observedSubjects()).containsExactlyInAnyOrder(
            new Subject("course:C1"),
            new Subject("student:S1")
        );
        assertThat(request.observedConsistencyKeys()).containsExactly(new ConsistencyKey("course:C1"));
    }

    @Test
    void rejectsAppendResultWithReversedPositions() {
        assertThatThrownBy(() -> new AppendResult(
            new Position(9),
            new Position(8),
            Map.of(new Subject("course:C1"), new SubjectRevision(8)),
            Map.of(),
            false
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("to position must not be before from position");
    }

    @Test
    void appendsThroughStorageNeutralContract() {
        AppendResult result = new AppendResult(
            new Position(101),
            new Position(101),
            Map.of(new Subject("course:C1"), new SubjectRevision(8)),
            Map.of(new ConsistencyKey("course:C1"), 12L),
            false
        );
        EventStore store = request -> CompletableFuture.completedFuture(result);

        assertThat(store.append(validAppendRequest()).toCompletableFuture().join()).isEqualTo(result);
    }

    @Test
    void carriesDiagnosticIdOnEventStoreExceptions() {
        EventStoreException failure = new ConsistencyConflict(
            "diag-20260729-17",
            new ConsistencyKey("course:C1"),
            41,
            42
        );

        assertThat(failure.diagnosticId()).isEqualTo("diag-20260729-17");
    }

    private static AppendRequest validAppendRequest() {
        return new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-course-c1-confirmed"),
            List.of(enrollmentConfirmed()),
            new ExpectedConsistency(Map.of(new ConsistencyKey("course:C1"), 11L)),
            Set.of(new ConsistencyKey("course:C1")),
            Durability.DURABLE
        );
    }

    private static EventData enrollmentConfirmed() {
        return new EventData(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1")),
            new EventType("EnrollmentConfirmed.v1"),
            new Subject("course:C1"),
            new ContentType("application/json"),
            new SchemaReference("schema://education/enrollment-confirmed/1"),
            "{\"course\":\"C1\"}".getBytes(StandardCharsets.UTF_8),
            "{\"trace\":\"T-17\"}".getBytes(StandardCharsets.UTF_8),
            Map.of("source", "registration")
        );
    }
}
