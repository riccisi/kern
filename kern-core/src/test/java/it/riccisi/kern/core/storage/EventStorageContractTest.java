package it.riccisi.kern.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        AppendResult result = new AppendResult(new SequencePosition(11), new SequencePosition(11), false);
        FakeStorage storage = new FakeStorage(result);
        PreparedAppend append = preparedAppend();

        CommitOutcome outcome = storage.commit(List.of(append), Durability.DURABLE);

        assertThat(storage.committed()).containsExactly(append);
        assertThat(outcome.results()).containsExactly(result);
        assertThat(outcome.highWatermark()).isEqualTo(new SequencePosition(11));
    }

    @Test
    void copiesMutableInputsAtSpiBoundary() {
        byte[] digest = "digest-17".getBytes(StandardCharsets.UTF_8);
        List<AppendResult> results = new ArrayList<>();
        results.add(appendResult());
        Set<QueryItem> items = new HashSet<>();
        items.add(new QueryItem(Set.of(new EventType("EnrollmentConfirmed.v1")), Set.of(new EventTag("course", "C1"))));

        PreparedAppend append = new PreparedAppend(
            validAppendRequest(),
            new RequestDigest(digest),
            "diag-17",
            Instant.parse("2026-07-29T13:00:00Z")
        );
        CommitOutcome outcome = new CommitOutcome(results, new SequencePosition(17));
        EventQuery query = new EventQuery(new ArrayList<>(items));
        digest[0] = 'X';
        results.clear();
        items.clear();

        assertThat(append.digest().bytes()).startsWith((byte) 'd');
        assertThat(outcome.results()).hasSize(1);
        assertThat(query.items()).hasSize(1);
    }

    @Test
    void rejectsInvalidStorageValuesEarly() {
        assertThatThrownBy(() -> new CommitOutcome(List.of(), new SequencePosition(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("append results must not be empty");

        assertThatThrownBy(() -> new ReadRequest(
            new Namespace("education"),
            new EventQuery(List.of()),
            new SequencePosition(0),
            0,
            Optional.empty()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("read limit must be positive");
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
            List.of(enrollmentConfirmed()),
            new AppendCondition(new EventQuery(List.of()), new SequencePosition(0)),
            new IdempotencyKey("append-course-c1-20260729")
        );
    }

    private static EventData enrollmentConfirmed() {
        return new EventData(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1")),
            new EventType("EnrollmentConfirmed.v1"),
            Set.of(new EventTag("course", "C1")),
            new ContentType("application/json"),
            "{\"student\":\"S1\"}".getBytes(StandardCharsets.UTF_8),
            "{\"trace\":\"t-17\"}".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static AppendResult appendResult() {
        return new AppendResult(new SequencePosition(17), new SequencePosition(17), false);
    }

    private static final class FakeStorage implements EventStorage {
        private final AppendResult result;
        private final List<PreparedAppend> committed;

        FakeStorage(final AppendResult result) {
            this.result = result;
            this.committed = new ArrayList<>();
        }

        @Override
        public CommitOutcome commit(final Iterable<PreparedAppend> appends, final Durability durability) {
            for (PreparedAppend append : appends) {
                this.committed.add(append);
            }
            return new CommitOutcome(List.of(this.result), this.result.toPosition());
        }

        @Override
        public ReadSnapshot snapshot() {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public StorageDiagnostics diagnostics() {
            return new StorageDiagnostics("fake", new SequencePosition(17), true, Map.of("write-buffer", "open"));
        }

        @Override
        public void flush(final FlushMode mode) {
        }

        @Override
        public void close() {
        }

        List<PreparedAppend> committed() {
            return List.copyOf(committed);
        }
    }
}
