package it.riccisi.kern.core.append;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AnyAppend;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
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
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.RequestDigest;
import it.riccisi.kern.core.storage.StorageDiagnostics;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

final class AppendCoordinatorInvariantTest {
    @Test
    void returnsTheResultAssignedByAtomicStorageCommit() {
        AppendResult assigned = new AppendResult(
            new Position(41),
            new Position(43),
            Map.of(
                new Subject("course:C1"), new SubjectRevision(6),
                new Subject("student:S1"), new SubjectRevision(1)
            ),
            Map.of(),
            false
        );
        FakeStorage storage = new FakeStorage(assigned);

        AppendResult result = coordinator(storage).append(request(List.of(
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b1", "course:C1"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b2", "student:S1"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b3", "course:C1")
        ))).toCompletableFuture().join();

        assertThat(result).isEqualTo(assigned);
        assertThat(storage.snapshotCalls()).isZero();
    }

    @Test
    void preservesCallerEventOrderInsidePreparedAppend() {
        FakeStorage storage = new FakeStorage(result());

        coordinator(storage).append(request(List.of(
            event("01890f70-7c6a-7d0b-9d01-86de05a9f51", "course:C1"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f52", "course:C1"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f53", "course:C1")
        ))).toCompletableFuture().join();

        assertThat(storage.committed().getFirst().request().events()).extracting(EventData::id).containsExactly(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f51")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f52")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f53"))
        );
    }

    @Test
    void exposesStorageConditionFailuresWithoutCommittingASecondAppend() {
        FakeStorage storage = new FakeStorage(new SubjectRevisionConflict(
            "diag-17",
            new Subject("course:C1"),
            new SubjectRevision(3),
            new SubjectRevision(4)
        ));

        assertThatThrownBy(() -> coordinator(storage).append(request(List.of(
            event("01890f70-7c6a-7d0b-9d01-86de05a9f61", "course:C1")
        ))).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(SubjectRevisionConflict.class);

        assertThat(storage.committed()).isEmpty();
    }

    private static AppendCoordinator coordinator(final EventStorage storage) {
        return new AppendCoordinator(
            storage,
            request -> new RequestDigest(
                ("digest:" + request.idempotencyKey().value()).getBytes(StandardCharsets.UTF_8)
            ),
            Clock.fixed(Instant.parse("2026-07-30T13:15:00Z"), ZoneOffset.UTC),
            new SequentialDiagnosticIds()
        );
    }

    private static AppendRequest request(final List<EventData> events) {
        return new AppendRequest(
            new Namespace("education"),
            new IdempotencyKey("append-coordinator-test"),
            events,
            new AnyAppend(),
            java.util.Set.of(),
            Durability.DURABLE
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

    private static AppendResult result() {
        return new AppendResult(
            new Position(41),
            new Position(41),
            Map.of(new Subject("course:C1"), new SubjectRevision(5)),
            Map.of(),
            false
        );
    }

    private static final class FakeStorage implements EventStorage {
        private final List<PreparedAppend> committed;
        private final AppendResult result;
        private final RuntimeException failure;
        private int snapshots;

        FakeStorage(final AppendResult result) {
            this.committed = new ArrayList<>();
            this.result = result;
            this.failure = null;
        }

        FakeStorage(final RuntimeException failure) {
            this.committed = new ArrayList<>();
            this.result = null;
            this.failure = failure;
        }

        @Override
        public CommitOutcome commit(final Iterable<PreparedAppend> appends, final Durability durability) {
            if (failure != null) {
                throw failure;
            }
            for (PreparedAppend append : appends) {
                this.committed.add(append);
            }
            return new CommitOutcome(List.of(result), result.toPosition());
        }

        @Override
        public ReadSnapshot snapshot() {
            this.snapshots += 1;
            throw new AssertionError("append coordinator must not open a snapshot");
        }

        @Override
        public StorageDiagnostics diagnostics() {
            return new StorageDiagnostics("fake", new Position(0), true, Map.of());
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

        int snapshotCalls() {
            return this.snapshots;
        }
    }
}
