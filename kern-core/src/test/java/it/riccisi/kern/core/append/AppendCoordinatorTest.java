package it.riccisi.kern.core.append;

import static org.assertj.core.api.Assertions.assertThat;

import it.riccisi.kern.api.append.AppendCondition;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.query.EventQuery;
import it.riccisi.kern.api.query.QueryItem;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.core.storage.CommitOutcome;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.RequestDigest;
import it.riccisi.kern.core.storage.StorageDiagnostics;
import it.riccisi.kern.core.storage.StoredEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AppendCoordinatorTest {
    @Test
    void returnsTheResultAssignedByAtomicStorageCommit() {
        AppendResult assigned = new AppendResult(new SequencePosition(41), new SequencePosition(43), false);
        FakeStorage storage = new FakeStorage(assigned);

        AppendResult result = coordinator(storage).append(request(List.of(
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b1"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b2"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f4b3")
        ))).toCompletableFuture().join();

        assertThat(result).isEqualTo(assigned);
        assertThat(storage.snapshotCalls()).isZero();
    }

    @Test
    void preservesCallerEventOrderInsidePreparedAppend() {
        FakeStorage storage = new FakeStorage(result());

        coordinator(storage).append(request(List.of(
            event("01890f70-7c6a-7d0b-9d01-86de05a9f51"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f52"),
            event("01890f70-7c6a-7d0b-9d01-86de05a9f53")
        ))).toCompletableFuture().join();

        assertThat(storage.committed().getFirst().request().events()).extracting(EventData::id).containsExactly(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f51")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f52")),
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f53"))
        );
    }

    @Test
    void delegatesReadsToOneSnapshot() {
        FakeStorage storage = new FakeStorage(result());
        ReadRequest request = new ReadRequest(
            new Namespace("education"),
            new EventQuery(List.of()),
            new SequencePosition(0),
            10,
            Optional.empty()
        );

        assertThat(coordinator(storage).read(request).observedAt()).isEqualTo(new SequencePosition(17));
        assertThat(storage.snapshotCalls()).isEqualTo(1);
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
        EventTag course = new EventTag("course", "C1");
        return new AppendRequest(
            new Namespace("education"),
            events,
            new AppendCondition(
                new EventQuery(List.of(new QueryItem(Set.of(), Set.of(course)))),
                new SequencePosition(0)
            ),
            new IdempotencyKey("append-coordinator-test")
        );
    }

    private static EventData event(final String id) {
        return new EventData(
            new EventId(UUID.fromString(id)),
            new EventType("EnrollmentConfirmed.v1"),
            Set.of(new EventTag("course", "C1")),
            new ContentType("application/json"),
            "{}".getBytes(StandardCharsets.UTF_8),
            "{\"trace\":\"T-14\"}".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static AppendResult result() {
        return new AppendResult(new SequencePosition(41), new SequencePosition(41), false);
    }

    private static final class FakeStorage implements EventStorage {
        private final List<PreparedAppend> committed;
        private final AppendResult result;
        private int snapshots;

        FakeStorage(final AppendResult result) {
            this.committed = new ArrayList<>();
            this.result = result;
        }

        @Override
        public CommitOutcome commit(final Iterable<PreparedAppend> appends, final Durability durability) {
            for (PreparedAppend append : appends) {
                this.committed.add(append);
            }
            return new CommitOutcome(List.of(result), result.toPosition());
        }

        @Override
        public ReadSnapshot snapshot() {
            this.snapshots += 1;
            return new ReadSnapshot() {
                @Override
                public QueryResult read(final ReadRequest request) {
                    return new QueryResult(List.of(), new SequencePosition(17), Optional.empty());
                }

                @Override
                public Optional<StoredEvent> eventById(final Namespace namespace, final EventId id) {
                    return Optional.empty();
                }

                @Override
                public SequencePosition highWatermark() {
                    return new SequencePosition(17);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public StorageDiagnostics diagnostics() {
            return new StorageDiagnostics("fake", new SequencePosition(0), true, Map.of());
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
