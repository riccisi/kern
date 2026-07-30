package it.riccisi.kern.core.append;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AnyAppend;
import it.riccisi.kern.api.append.AppendCondition;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.append.ExpectedSubjectRevision;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
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
import it.riccisi.kern.core.storage.CommitOutcome;
import it.riccisi.kern.core.storage.Direction;
import it.riccisi.kern.core.storage.EventPage;
import it.riccisi.kern.core.storage.EventQuery;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.RequestDigest;
import it.riccisi.kern.core.storage.RevisionQuery;
import it.riccisi.kern.core.storage.Revisions;
import it.riccisi.kern.core.storage.StorageDiagnostics;
import it.riccisi.kern.core.storage.StoredEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class AppendCoordinatorInvariantTest {
    @Test
    void assignsGlobalPositionsAndSubjectRevisions() {
        FakeStorage storage = new FakeStorage(
            new Position(40),
            Map.of(new Subject("course:C1"), new SubjectRevision(4))
        );

        AppendResult result = coordinator(storage).append(new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-c1-and-s1"),
            List.of(
                event("01890f70-7c6a-7d0b-9d01-86de05a9f4b1", "course:C1"),
                event("01890f70-7c6a-7d0b-9d01-86de05a9f4b2", "student:S1"),
                event("01890f70-7c6a-7d0b-9d01-86de05a9f4b3", "course:C1")
            ),
            new AnyAppend(),
            Set.of(new ConsistencyKey("course:C1")),
            Durability.DURABLE
        )).toCompletableFuture().join();

        assertThat(result).isEqualTo(new AppendResult(
            new Position(41),
            new Position(43),
            Map.of(
                new Subject("course:C1"), new SubjectRevision(6),
                new Subject("student:S1"), new SubjectRevision(1)
            ),
            Map.of(),
            false
        ));
    }

    @Test
    void keepsEventOrderInsidePreparedAppend() {
        FakeStorage storage = new FakeStorage(new Position(12), Map.of());

        coordinator(storage).append(new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-ordered-batch"),
            List.of(
                event("01890f70-7c6a-7d0b-9d01-86de05a9f51", "course:C1"),
                event("01890f70-7c6a-7d0b-9d01-86de05a9f52", "course:C1"),
                event("01890f70-7c6a-7d0b-9d01-86de05a9f53", "course:C1")
            ),
            new AnyAppend(),
            Set.of(),
            Durability.RELAXED
        )).toCompletableFuture().join();

        assertThat(storage.committed().getFirst().request().events()).extracting(EventData::id).containsExactly(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f51")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f52")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f53"))
        );
    }

    @Test
    void rejectsStaleExpectedSubjectRevisionWithoutCommit() {
        FakeStorage storage = new FakeStorage(
            new Position(40),
            Map.of(new Subject("course:C1"), new SubjectRevision(4))
        );

        assertThatThrownBy(() -> coordinator(storage).append(new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-stale-c1"),
            List.of(event("01890f70-7c6a-7d0b-9d01-86de05a9f61", "course:C1")),
            new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(3)),
            Set.of(),
            Durability.DURABLE
        )).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(SubjectRevisionConflict.class);

        assertThat(storage.committed()).isEmpty();
    }

    @Test
    void keepsFollowingAppendIndependentAfterConflict() {
        FakeStorage storage = new FakeStorage(
            new Position(40),
            Map.of(new Subject("course:C1"), new SubjectRevision(4))
        );
        AppendCoordinator coordinator = coordinator(storage);
        coordinator.append(new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-stale-c1"),
            List.of(event("01890f70-7c6a-7d0b-9d01-86de05a9f71", "course:C1")),
            new ExpectedSubjectRevision(new Subject("course:C1"), new SubjectRevision(3)),
            Set.of(),
            Durability.DURABLE
        ));

        AppendResult result = coordinator.append(new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-independent-c1"),
            List.of(event("01890f70-7c6a-7d0b-9d01-86de05a9f72", "course:C1")),
            new AnyAppend(),
            Set.of(),
            Durability.DURABLE
        )).toCompletableFuture().join();

        assertThat(result.fromPosition()).isEqualTo(new Position(41));
    }

    private static AppendCoordinator coordinator(final EventStorage storage) {
        return new AppendCoordinator(
            storage,
            request -> new RequestDigest(
                ("digest:" + request.idempotencyKey().value()).getBytes(StandardCharsets.UTF_8)
            ),
            Clock.fixed(Instant.parse("2026-07-30T13:15:00Z"), ZoneOffset.UTC)
        );
    }

    private static EventData event(final String id, final String subject) {
        return new EventData(
            new EventId(UUID.fromString(id)),
            new EventType("EnrollmentConfirmed.v1"),
            new Subject(subject),
            new ContentType("application/json"),
            new SchemaReference("schema://education/enrollment-confirmed/1"),
            ("{\"subject\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8),
            "{\"trace\":\"T-14\"}".getBytes(StandardCharsets.UTF_8),
            Map.of("source", "registration")
        );
    }

    private static final class FakeStorage implements EventStorage {
        private final List<PreparedAppend> committed;
        private final Map<Subject, SubjectRevision> subjects;
        private Position highWatermark;

        FakeStorage(final Position highWatermark, final Map<Subject, SubjectRevision> subjects) {
            this.committed = new ArrayList<>();
            this.subjects = new HashMap<>(subjects);
            this.highWatermark = highWatermark;
        }

        @Override
        public CommitOutcome commit(final Iterable<PreparedAppend> appends, final Durability durability) {
            List<AppendResult> results = new ArrayList<>();
            for (PreparedAppend append : appends) {
                this.committed.add(append);
                results.add(append.result());
                this.highWatermark = append.result().toPosition();
                this.subjects.putAll(append.result().subjectRevisions());
            }
            return new CommitOutcome(results, this.highWatermark);
        }

        @Override
        public ReadSnapshot snapshot() {
            return new FakeSnapshot(this.highWatermark, Map.copyOf(this.subjects));
        }

        @Override
        public StorageDiagnostics diagnostics() {
            return new StorageDiagnostics("fake", this.highWatermark, true, Map.of());
        }

        @Override
        public void flush(final FlushMode mode) {
        }

        @Override
        public void close() {
        }

        List<PreparedAppend> committed() {
            return List.copyOf(this.committed);
        }
    }

    private static final class FakeSnapshot implements ReadSnapshot {
        private final Position highWatermark;
        private final Map<Subject, SubjectRevision> subjects;

        FakeSnapshot(final Position highWatermark, final Map<Subject, SubjectRevision> subjects) {
            this.highWatermark = highWatermark;
            this.subjects = subjects;
        }

        @Override
        public EventPage read(final EventQuery query) {
            return new EventPage(List.of(), Optional.empty(), this.highWatermark);
        }

        @Override
        public Revisions revisions(final RevisionQuery query) {
            return new Revisions(this.subjects, Map.of(), this.highWatermark);
        }

        @Override
        public Optional<StoredEvent> eventById(final Namespace namespace, final EventId id) {
            return Optional.empty();
        }

        @Override
        public Position highWatermark() {
            return this.highWatermark;
        }

        @Override
        public void close() {
        }
    }
}
