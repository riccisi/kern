package it.riccisi.kern.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AppendCondition;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.error.QueryConflict;
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
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.RequestDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

final class RocksEventStorageTest {
    private static final EventType STUDENT_ENROLLED = new EventType("StudentEnrolled.v1");
    private static final EventType STUDENT_ADDRESS_CHANGED = new EventType("StudentAddressChanged.v1");
    private static final EventTag STUDENT = new EventTag("student", "S1");
    private static final EventTag OTHER_STUDENT = new EventTag("student", "S2");
    private static final EventTag COURSE = new EventTag("course", "C1");

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
                    "event_ids",
                    "tag_type_index",
                    "type_index",
                    "tag_index",
                    "idempotency",
                    "metadata",
                    "diagnostics"
                );
        }
    }

    @Test
    void commitsOneLogicalRequestAtomically(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-alpha");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            CommitOutcome outcome = storage.commit(
                List.of(prepared(
                    namespace,
                    List.of(
                        event("issued", new EventType("InvoiceIssued.v1"), new EventTag("invoice", "7841")),
                        event("paid", new EventType("InvoicePaid.v1"), new EventTag("invoice", "7841"))
                    ),
                    condition(new EventQuery(List.of()), new SequencePosition(0))
                )),
                Durability.DURABLE
            );

            assertThat(outcome.onlyResult().fromPosition()).isEqualTo(new SequencePosition(1));
            assertThat(outcome.onlyResult().toPosition()).isEqualTo(new SequencePosition(2));
            assertThat(outcome.highWatermark()).isEqualTo(new SequencePosition(2));
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark()).isEqualTo(new SequencePosition(2));
        }
    }

    @Test
    void rejectsGroupCommitUntilOverlayExists(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-bravo");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            PreparedAppend first = prepared(namespace, List.of(event("one", STUDENT_ENROLLED, STUDENT)), noConflict());
            PreparedAppend second = prepared(namespace, List.of(event("two", STUDENT_ENROLLED, COURSE)), noConflict());

            assertThatThrownBy(() -> storage.commit(List.of(first, second), Durability.DURABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("group commit requires an overlay and is not enabled");
        }
    }

    @Test
    void returnsOneMultiTagEventThroughEveryApplicableQuery(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-charlie");
        EventData enrolled = event("enrolled", STUDENT_ENROLLED, STUDENT, COURSE);
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(List.of(prepared(namespace, List.of(enrolled), noConflict())), Durability.DURABLE);

            try (ReadSnapshot snapshot = storage.snapshot()) {
                QueryResult byStudent = snapshot.read(read(namespace, query(STUDENT), new SequencePosition(0)));
                QueryResult byCourse = snapshot.read(read(namespace, query(COURSE), new SequencePosition(0)));

                assertThat(byStudent.events()).singleElement()
                    .satisfies(event -> assertThat(event.event().id()).isEqualTo(enrolled.id()));
                assertThat(byCourse.events()).singleElement()
                    .satisfies(event -> assertThat(event.event().id()).isEqualTo(enrolled.id()));
                assertThat(snapshot.eventById(namespace, enrolled.id()))
                    .hasValueSatisfying(event -> assertThat(event.data()).isEqualTo(enrolled));
            }
        }
    }

    @Test
    void observedAtMayExceedTheLastMatchingPosition(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-delta");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(prepared(namespace, List.of(event("address", STUDENT_ADDRESS_CHANGED, STUDENT)), noConflict())),
                Durability.DURABLE
            );

            try (ReadSnapshot snapshot = storage.snapshot()) {
                QueryResult result = snapshot.read(read(namespace, query(STUDENT_ENROLLED, STUDENT), new SequencePosition(0)));

                assertThat(result.events()).isEmpty();
                assertThat(result.observedAt()).isEqualTo(new SequencePosition(1));
            }
        }
    }

    @Test
    void ignoresLaterEventsThatDoNotMatchTheConditionType(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-echo");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(prepared(namespace, List.of(event("address", STUDENT_ADDRESS_CHANGED, STUDENT)), noConflict())),
                Durability.DURABLE
            );

            CommitOutcome outcome = storage.commit(
                List.of(prepared(
                    namespace,
                    List.of(event("enrolled", STUDENT_ENROLLED, STUDENT, COURSE)),
                    condition(query(STUDENT_ENROLLED, STUDENT), new SequencePosition(0))
                )),
                Durability.DURABLE
            );

            assertThat(outcome.highWatermark()).isEqualTo(new SequencePosition(2));
        }
    }

    @Test
    void rejectsLaterEventsThatMatchTheCompleteCondition(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-foxtrot");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(prepared(namespace, List.of(event("s2-enrolled", STUDENT_ENROLLED, OTHER_STUDENT, COURSE)), noConflict())),
                Durability.DURABLE
            );

            assertThatThrownBy(() -> storage.commit(
                List.of(prepared(
                    namespace,
                    List.of(event("s1-enrolled", STUDENT_ENROLLED, STUDENT, COURSE)),
                    condition(query(STUDENT_ENROLLED, COURSE), new SequencePosition(0))
                )),
                Durability.DURABLE
            ))
                .isInstanceOf(QueryConflict.class)
                .satisfies(error -> assertThat(((QueryConflict) error).conflictingPosition())
                    .isEqualTo(new SequencePosition(1)));
        }
    }

    @Test
    void flushesCommittedData(@TempDir final Path directory) {
        Namespace namespace = new Namespace("tenant-golf");
        try (RocksEventStorage storage = new RocksEventStorage(directory)) {
            storage.commit(
                List.of(prepared(namespace, List.of(event("issued", new EventType("InvoiceIssued.v1"), new EventTag("invoice", "4096"))), noConflict())),
                Durability.RELAXED
            );
            storage.flush(FlushMode.SYNC);
        }
        try (RocksEventStorage reopened = new RocksEventStorage(directory)) {
            assertThat(reopened.diagnostics().highWatermark()).isEqualTo(new SequencePosition(1));
        }
    }

    private static ReadRequest read(final Namespace namespace, final EventQuery query, final SequencePosition from) {
        return new ReadRequest(namespace, query, from, 10, Optional.empty());
    }

    private static AppendCondition noConflict() {
        return condition(new EventQuery(List.of()), new SequencePosition(0));
    }

    private static AppendCondition condition(final EventQuery query, final SequencePosition after) {
        return new AppendCondition(query, after);
    }

    private static EventQuery query(final EventTag tag) {
        return new EventQuery(List.of(new QueryItem(Set.of(), Set.of(tag))));
    }

    private static EventQuery query(final EventType type, final EventTag tag) {
        return new EventQuery(List.of(new QueryItem(Set.of(type), Set.of(tag))));
    }

    private static PreparedAppend prepared(
        final Namespace namespace,
        final List<EventData> events,
        final AppendCondition condition
    ) {
        String request = events.getFirst().id().toString();
        return new PreparedAppend(
            new AppendRequest(
                namespace,
                events,
                condition,
                new IdempotencyKey("request-" + request)
            ),
            new RequestDigest(("digest:" + request).getBytes(StandardCharsets.UTF_8)),
            request,
            Instant.parse("2026-07-30T13:15:00Z")
        );
    }

    private static EventData event(final String seed, final EventType type, final EventTag... tags) {
        return new EventData(
            new EventId(UUID.nameUUIDFromBytes(("event:" + seed).getBytes(StandardCharsets.UTF_8))),
            type,
            Set.of(tags),
            new ContentType("application/json"),
            ("{\"event\":\"" + seed + "\"}").getBytes(StandardCharsets.UTF_8),
            "{}".getBytes(StandardCharsets.UTF_8)
        );
    }
}
