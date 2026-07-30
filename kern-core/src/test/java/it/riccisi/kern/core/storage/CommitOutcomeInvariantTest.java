package it.riccisi.kern.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CommitOutcomeInvariantTest {
    @Test
    void acceptsContiguousCommittedPositionRanges() {
        assertThat(new CommitOutcome(
            List.of(
                result(41, 42),
                result(43, 43)
            ),
            new Position(43)
        ).highWatermark()).isEqualTo(new Position(43));
    }

    @Test
    void returnsTheOnlyResult() {
        AppendResult result = result(41, 41);

        assertThat(new CommitOutcome(List.of(result), new Position(41)).onlyResult()).isEqualTo(result);
    }

    @Test
    void rejectsRequestingOnlyResultFromBatchOutcome() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(result(41, 41), result(42, 42)),
            new Position(42)
        ).onlyResult())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("commit outcome does not contain exactly one append result");
    }

    @Test
    void rejectsCommittedPositionGaps() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(
                result(41, 41),
                result(43, 43)
            ),
            new Position(43)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("append result positions must be contiguous");
    }

    @Test
    void rejectsOutOfOrderCommittedRanges() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(
                result(43, 43),
                result(41, 42)
            ),
            new Position(43)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("append result positions must be contiguous");
    }

    @Test
    void rejectsHighWatermarkDifferentFromCommittedRangeEnd() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(
                result(41, 42),
                result(43, 43)
            ),
            new Position(44)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("high watermark must match committed append results");
    }

    private static AppendResult result(final long from, final long to) {
        return new AppendResult(
            new Position(from),
            new Position(to),
            Map.of(new Subject("course:C1"), new SubjectRevision(to - 37)),
            Map.of(new ConsistencyKey("course:C1"), to + 11),
            false
        );
    }
}
