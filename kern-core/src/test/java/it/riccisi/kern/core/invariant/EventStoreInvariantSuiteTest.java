package it.riccisi.kern.core.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class EventStoreInvariantSuiteTest {
    private static final Pattern ISSUE = Pattern.compile("https://github\\.com/riccisi/kern/issues/\\d+");

    @Test
    void declaresTheFoundationInvariantSet() {
        assertThat(catalog()).extracting(Invariant::id).containsExactlyInAnyOrder(
            "global-positions-are-unique-and-contiguous",
            "append-batch-preserves-request-order",
            "durable-append-survives-restart",
            "append-batch-is-atomic",
            "idempotency-replay-adds-no-events",
            "idempotency-conflict-rejects-different-request",
            "query-condition-rejects-later-matching-event",
            "rebuilt-indexes-are-query-equivalent",
            "subscription-from-position-omits-no-matching-event"
        );
    }

    @Test
    void keepsEveryInvariantDeliberatelyClassified() {
        assertThat(catalog()).allSatisfy((final Invariant invariant) -> {
            assertThat(invariant.statement()).isNotBlank();
            assertThat(invariant.layer()).isIn(
                "core unit",
                "storage integration",
                "concurrency",
                "fault injection",
                "subscription integration"
            );
            assertThat(invariant.status()).isIn(Status.EXECUTABLE, Status.PENDING);
        });
    }

    @Test
    void pointsPendingInvariantsToTrackingIssues() {
        assertThat(catalog().stream().filter(invariant -> invariant.status() == Status.PENDING))
            .allSatisfy((final Invariant invariant) ->
                assertThat(ISSUE.matcher(invariant.trackingIssue()).matches()).isTrue()
            );
    }

    @Test
    void doesNotClaimExecutableCoverageBeforeStorageExists() {
        assertThat(catalog().stream().filter(invariant -> invariant.status() == Status.EXECUTABLE)).isEmpty();
    }

    private static List<Invariant> catalog() {
        return List.of(
            new Invariant(
                "global-positions-are-unique-and-contiguous",
                "Committed positions are unique, strictly increasing, and have no ordinary-path gaps",
                "storage integration",
                Status.PENDING,
                "RocksDB commit position invariant test",
                "https://github.com/riccisi/kern/issues/15"
            ),
            new Invariant(
                "append-batch-preserves-request-order",
                "Events inside an accepted append batch keep the caller-provided order",
                "storage integration",
                Status.PENDING,
                "RocksDB append ordering invariant test",
                "https://github.com/riccisi/kern/issues/15"
            ),
            new Invariant(
                "durable-append-survives-restart",
                "An append acknowledged as durable is present after storage restart",
                "storage integration",
                Status.PENDING,
                "RocksDB restart invariant test",
                "https://github.com/riccisi/kern/issues/15"
            ),
            new Invariant(
                "append-batch-is-atomic",
                "A committed append batch is totally present or totally absent",
                "storage integration",
                Status.PENDING,
                "RocksDB atomic write-batch invariant test",
                "https://github.com/riccisi/kern/issues/15"
            ),
            new Invariant(
                "idempotency-replay-adds-no-events",
                "Replaying the same idempotency key and request returns the original result without appending events",
                "core unit",
                Status.PENDING,
                "Idempotency replay invariant test",
                "https://github.com/riccisi/kern/issues/16"
            ),
            new Invariant(
                "idempotency-conflict-rejects-different-request",
                "Reusing an idempotency key with a different request is rejected",
                "core unit",
                Status.PENDING,
                "Idempotency conflict invariant test",
                "https://github.com/riccisi/kern/issues/16"
            ),
            new Invariant(
                "query-condition-rejects-later-matching-event",
                "A conditional append does not commit when a later event matches its complete query",
                "concurrency",
                Status.PENDING,
                "Query conditional append invariant test",
                "https://github.com/riccisi/kern/issues/23"
            ),
            new Invariant(
                "rebuilt-indexes-are-query-equivalent",
                "Indexes rebuilt from the event log produce the same query results as the original indexes",
                "fault injection",
                Status.PENDING,
                "Index rebuild equivalence invariant test",
                "https://github.com/riccisi/kern/issues/18"
            ),
            new Invariant(
                "subscription-from-position-omits-no-matching-event",
                "A subscription from position N does not omit matching committed events after N",
                "subscription integration",
                Status.PENDING,
                "Subscription no-omission invariant test",
                "https://github.com/riccisi/kern/issues/19"
            )
        );
    }

    private record Invariant(
        String id,
        String statement,
        String layer,
        Status status,
        String coverage,
        String trackingIssue
    ) {
        private Invariant {
            List.of(id, statement, layer, coverage, trackingIssue).forEach((final String value) -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("invariant field must not be blank");
                }
            });
        }
    }

    private enum Status {
        EXECUTABLE,
        PENDING
    }
}
