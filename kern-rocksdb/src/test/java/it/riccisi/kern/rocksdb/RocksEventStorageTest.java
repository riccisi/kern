package it.riccisi.kern.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AnyAppend;
import it.riccisi.kern.api.append.AppendCondition;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.append.ExpectedConsistency;
import it.riccisi.kern.api.append.ExpectedSubjectRevision;
import it.riccisi.kern.api.error.SubjectRevisionConflict;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.AllSubjects;
import it.riccisi.kern.core.storage.CommitOutcome;
import it.riccisi.kern.core.storage.Direction;
import it.riccisi.kern.core.storage.EventPage;
import it.riccisi.kern.core.storage.EventQuery;
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.Revisions;
import it.riccisi.kern.core.storage.RevisionQuery;
import it.riccisi.kern.core.storage.RequestDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocksEventStorageTest {

    @Test
    void reopensAnEmptyStorage(@TempDir final Path directory) {
        try (RocksEventStorage first = new RocksEventStorage(directory)) {
            assertThat(first.diagnostics().highWatermark().value()).isZero();
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().engine()).isEqualTo("RocksDB");
        }
    }

    @Test
    void opensDedicatedColumnFamilies(@TempDir final Path directory) throws RocksDBException {
        try (RocksEventStorage ignored = new RocksEventStorage(directory)) {
            ignored.diagnostics();
        }
        try (Options options = new Options()) {
            assertThat(RocksDB.listColumnFamilies(options, directory.toString()).stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                    "default",
                    "events",
                    "subject-revisions",
                    "event-ids",
                    "types",
                    "tags",
                    "subject-heads",
                    "consistency",
                    "idempotency",
                    "system"
                );
        }
    }

    @Test
    void commitsMultipleAppendsAtomically(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-alpha");
        Subject subject = new Subject("invoice-7841");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            CommitOutcome outcome = storage.commit(
                List.of(
                    prepared(namespace, subject, "issued", "request-issue"),
                    prepared(namespace, subject, "paid", "request-paid")
                ),
                Durability.DURABLE
            );

            assertThat(outcome)
                .satisfies(result -> assertThat(result.results()).hasSize(2))
                .satisfies(result -> assertThat(result.results().getFirst().fromPosition().value()).isEqualTo(1L))
                .satisfies(result -> assertThat(result.results().getFirst().toPosition().value()).isEqualTo(1L))
                .satisfies(result -> assertThat(result.results().getFirst().subjectRevisions().get(subject).value()).isEqualTo(1L))
                .satisfies(result -> assertThat(result.results().get(1).fromPosition().value()).isEqualTo(2L))
                .satisfies(result -> assertThat(result.results().get(1).toPosition().value()).isEqualTo(2L))
                .satisfies(result -> assertThat(result.results().get(1).subjectRevisions().get(subject).value()).isEqualTo(2L))
                .satisfies(result -> assertThat(result.highWatermark().value()).isEqualTo(2L));
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark().value()).isEqualTo(2L);
        }
    }

    @Test
    void flushesCommittedData(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-echo");
        Subject subject = new Subject("invoice-4096");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(prepared(namespace, subject, "issued", "request-issue")),
                Durability.RELAXED
            );
            storage.flush(FlushMode.SYNC);
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark().value()).isEqualTo(1L);
        }
    }

    @Test
    void assignsDistinctPositionsToConcurrentCommits(@TempDir final Path directory) throws Exception {
        Namespace namespace = new Namespace("tenant-foxtrot");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            List<Callable<Long>> commits = List.of(
                () -> storage.commit(
                    List.of(prepared(namespace, new Subject("invoice-1001"), "issued", "request-1001")),
                    Durability.DURABLE
                ).onlyResult().fromPosition().value(),
                () -> storage.commit(
                    List.of(prepared(namespace, new Subject("invoice-1002"), "issued", "request-1002")),
                    Durability.DURABLE
                ).onlyResult().fromPosition().value()
            );

            List<Future<Long>> positions = executor.invokeAll(commits, 5, TimeUnit.SECONDS);

            assertThat(positions)
                .allSatisfy(position -> assertThat(position.isCancelled()).isFalse())
                .extracting(position -> position.get(1, TimeUnit.SECONDS))
                .containsExactlyInAnyOrder(1L, 2L);
        } finally {
            executor.shutdownNow();
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark().value()).isEqualTo(2L);
        }
    }

    @Test
    void persistsConsistencyRevisions(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-charlie");
        Subject subject = new Subject("invoice-1024");
        ConsistencyKey key = new ConsistencyKey("customer:881");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            CommitOutcome outcome = storage.commit(
                List.of(prepared(namespace, subject, "issued", "request-issue", new AnyAppend(), Set.of(key))),
                Durability.DURABLE
            );

            assertThat(outcome.onlyResult().consistencyRevisions().get(key)).isEqualTo(new ConsistencyRevision(1));
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            CommitOutcome outcome = reopened.commit(
                List.of(
                    prepared(
                        namespace,
                        subject,
                        "paid",
                        "request-paid",
                        new ExpectedConsistency(Map.of(key, new ConsistencyRevision(1))),
                        Set.of(key)
                    )
                ),
                Durability.DURABLE
            );

            assertThat(outcome.onlyResult().consistencyRevisions().get(key)).isEqualTo(new ConsistencyRevision(2));
        }
    }

    @Test
    void exposesCommittedEventsThroughSnapshots(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-delta");
        Subject subject = new Subject("invoice-2048");
        ConsistencyKey key = new ConsistencyKey("customer:314");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(
                    prepared(namespace, subject, "issued", "request-issue", new AnyAppend(), Set.of(key)),
                    prepared(namespace, subject, "paid", "request-paid")
                ),
                Durability.DURABLE
            );

            try (ReadSnapshot snapshot = storage.snapshot()) {
                EventPage page = snapshot.read(new EventQuery(
                    namespace,
                    new AllSubjects(),
                    Set.of(new EventType("invoice.paid")),
                    Map.of("kind", "paid"),
                    new Position(0),
                    10,
                    Direction.FORWARD
                ));
                Revisions revisions = snapshot.revisions(
                    new RevisionQuery(namespace, Set.of(subject), Set.of(key))
                );

                assertThat(page.events())
                    .singleElement()
                    .satisfies(event -> assertThat(event.position().value()).isEqualTo(2L))
                    .satisfies(event -> assertThat(snapshot.eventById(namespace, event.data().id())).contains(event));
                assertThat(revisions.subjectRevision(subject).value()).isEqualTo(2L);
                assertThat(revisions.consistencyRevision(key)).isEqualTo(new ConsistencyRevision(1));
                assertThat(snapshot.highWatermark().value()).isEqualTo(2L);
            }
        }
    }

    @Test
    void readsTheLatestEventsFirstWhenScanningBackward(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-golf");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(
                    prepared(namespace, new Subject("invoice-3001"), "issued", "request-3001"),
                    prepared(namespace, new Subject("invoice-3002"), "paid", "request-3002")
                ),
                Durability.DURABLE
            );

            try (ReadSnapshot snapshot = storage.snapshot()) {
                EventPage page = snapshot.read(new EventQuery(
                    namespace,
                    new AllSubjects(),
                    Set.of(),
                    Map.of(),
                    new Position(0),
                    10,
                    Direction.BACKWARD
                ));

                assertThat(page.events())
                    .extracting(event -> event.position().value())
                    .containsExactly(2L, 1L);
            }
        }
    }

    @Test
    void rejectsAConflictingAppendWithoutPersistingTheBatch(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-bravo");
        Subject subject = new Subject("invoice-9135");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            assertThatThrownBy(() -> storage.commit(
                List.of(
                    prepared(namespace, subject, "issued", "request-issue"),
                    prepared(
                        namespace,
                        subject,
                        "paid",
                        "request-paid",
                        new ExpectedSubjectRevision(subject, new SubjectRevision(9))
                    )
                ),
                Durability.DURABLE
            )).isInstanceOf(SubjectRevisionConflict.class);
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark().value()).isZero();
        }
    }

    private static PreparedAppend prepared(
        final Namespace namespace,
        final Subject subject,
        final String event,
        final String request
    ) {
        return prepared(namespace, subject, event, request, new AnyAppend());
    }

    private static PreparedAppend prepared(
        final Namespace namespace,
        final Subject subject,
        final String event,
        final String request,
        final AppendCondition condition
    ) {
        return prepared(namespace, subject, event, request, condition, Set.of());
    }

    private static PreparedAppend prepared(
        final Namespace namespace,
        final Subject subject,
        final String event,
        final String request,
        final AppendCondition condition,
        final Set<ConsistencyKey> touched
    ) {
        return new PreparedAppend(
            new AppendRequest(
                namespace,
                new IdempotencyKey(request),
                List.of(data(subject, event)),
                condition,
                touched,
                Durability.DURABLE
            ),
            new RequestDigest(("digest:" + request).getBytes(StandardCharsets.UTF_8)),
            request,
            Instant.parse("2026-07-30T13:15:00Z")
        );
    }

    private static EventData data(final Subject subject, final String event) {
        return new EventData(
            new EventId(UUID.nameUUIDFromBytes(("event:" + event).getBytes(StandardCharsets.UTF_8))),
            new EventType("invoice." + event),
            subject,
            new ContentType("application/json"),
            new SchemaReference("invoice-event:v1"),
            ("{\"event\":\"" + event + "\"}").getBytes(StandardCharsets.UTF_8),
            ("metadata:" + event).getBytes(StandardCharsets.UTF_8),
            Map.of("source", "rocks-storage-test", "kind", event)
        );
    }
}
