package it.riccisi.kern.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AnyAppend;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EventStorageContractTest {
    @Test
    void commitsPreparedAppendBatchThroughStorageBoundary() {
        AppendResult result = new AppendResult(
            new Position(11),
            new Position(11),
            Map.of(new Subject("course:C1"), new SubjectRevision(4)),
            Map.of(new ConsistencyKey("course:C1"), 9L),
            false
        );
        FakeStorage storage = new FakeStorage(result);
        PreparedAppend append = preparedAppend();

        CommitOutcome outcome = storage.commit(List.of(append), Durability.DURABLE);

        assertThat(storage.committed()).containsExactly(append);
        assertThat(outcome.results()).containsExactly(result);
        assertThat(outcome.highWatermark()).isEqualTo(new Position(11));
    }

    @Test
    void callerOwnsReadSnapshotLifecycle() {
        FakeStorage storage = new FakeStorage(appendResult());
        TrackingSnapshot snapshot = storage.snapshot();

        try (snapshot) {
            snapshot.revisions(
                new RevisionQuery(
                    new Namespace("education"),
                    Set.of(new Subject("course:C1")),
                    Set.of(new ConsistencyKey("course:C1"))
                )
            );
        }

        assertThat(snapshot.closed()).isTrue();
    }

    @Test
    void copiesMutableInputsAtSpiBoundary() {
        byte[] digest = "digest-17".getBytes(StandardCharsets.UTF_8);
        List<AppendResult> results = new ArrayList<>();
        results.add(appendResult());
        Set<EventType> types = new HashSet<>();
        types.add(new EventType("EnrollmentConfirmed.v1"));
        Map<String, String> tags = new java.util.HashMap<>();
        tags.put("source", "registration");

        PreparedAppend append = new PreparedAppend(
            validAppendRequest(),
            new RequestDigest(digest),
            "diag-17",
            Instant.parse("2026-07-29T13:00:00Z")
        );
        CommitOutcome outcome = new CommitOutcome(results, new Position(17));
        EventQuery query = new EventQuery(
            new Namespace("education"),
            new SingleSubject(new Subject("course:C1")),
            types,
            tags,
            new Position(16),
            25,
            Direction.FORWARD
        );
        digest[0] = 'X';
        results.clear();
        types.clear();
        tags.clear();

        assertThat(append.digest().bytes()).startsWith((byte) 'd');
        assertThat(outcome.results()).hasSize(1);
        assertThat(query.types()).containsExactly(new EventType("EnrollmentConfirmed.v1"));
        assertThat(query.exactTags()).containsEntry("source", "registration");
    }

    @Test
    void rejectsInvalidStorageValuesEarly() {
        assertThatThrownBy(() -> new CommitOutcome(List.of(), new Position(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("append results must not be empty");

        assertThatThrownBy(() -> new EventQuery(
            new Namespace("education"),
            new AllSubjects(),
            Set.of(),
            Map.of(),
            new Position(0),
            0,
            Direction.FORWARD
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be positive");

        assertThatThrownBy(() -> new Revisions(
            Map.of(),
            Map.of(new ConsistencyKey("course:C1"), -1L),
            new Position(0)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consistency revision must not be negative");
    }

    private static PreparedAppend preparedAppend() {
        return new PreparedAppend(
            validAppendRequest(),
            new RequestDigest("request-digest-17".getBytes(StandardCharsets.UTF_8)),
            "diag-20260729-17",
            Instant.parse("2026-07-29T13:00:00Z")
        );
    }

    private static AppendRequest validAppendRequest() {
        return new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-course-c1-20260729"),
            List.of(enrollmentConfirmed()),
            new AnyAppend(),
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
            "{\"student\":\"S1\"}".getBytes(StandardCharsets.UTF_8),
            "{\"trace\":\"t-17\"}".getBytes(StandardCharsets.UTF_8),
            Map.of("source", "registration")
        );
    }

    private static AppendResult appendResult() {
        return new AppendResult(
            new Position(17),
            new Position(17),
            Map.of(new Subject("course:C1"), new SubjectRevision(4)),
            Map.of(new ConsistencyKey("course:C1"), 9L),
            false
        );
    }

    private static final class FakeStorage implements EventStorage {
        private final AppendResult result;
        private final List<PreparedAppend> committed;

        FakeStorage(AppendResult result) {
            this.result = result;
            this.committed = new ArrayList<>();
        }

        @Override
        public CommitOutcome commit(Iterable<PreparedAppend> appends, Durability durability) {
            for (PreparedAppend append : appends) {
                this.committed.add(append);
            }
            return new CommitOutcome(List.of(this.result), this.result.toPosition());
        }

        @Override
        public TrackingSnapshot snapshot() {
            return new TrackingSnapshot(new Position(17));
        }

        @Override
        public StorageDiagnostics diagnostics() {
            return new StorageDiagnostics("fake", new Position(17), true, Map.of("write-buffer", "open"));
        }

        @Override
        public void flush(FlushMode mode) {
        }

        @Override
        public void close() {
        }

        List<PreparedAppend> committed() {
            return List.copyOf(this.committed);
        }
    }

    private static final class TrackingSnapshot implements ReadSnapshot {
        private final Position highWatermark;
        private boolean closed;

        TrackingSnapshot(Position highWatermark) {
            this.highWatermark = highWatermark;
            this.closed = false;
        }

        @Override
        public Position highWatermark() {
            return this.highWatermark;
        }

        @Override
        public EventPage read(EventQuery query) {
            return new EventPage(List.of(), Optional.empty(), this.highWatermark);
        }

        @Override
        public Revisions revisions(RevisionQuery query) {
            return new Revisions(
                Map.of(new Subject("course:C1"), new SubjectRevision(4)),
                Map.of(new ConsistencyKey("course:C1"), 9L),
                this.highWatermark
            );
        }

        @Override
        public Optional<StoredEvent> eventById(Namespace namespace, EventId id) {
            return Optional.empty();
        }

        @Override
        public void close() {
            this.closed = true;
        }

        boolean closed() {
            return this.closed;
        }
    }
}
