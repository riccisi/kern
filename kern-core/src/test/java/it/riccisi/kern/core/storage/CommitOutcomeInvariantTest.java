package it.riccisi.kern.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommitOutcomeInvariantTest {
    @Test
    void acceptsContiguousCommittedPositionRanges() {
        assertThat(new CommitOutcome(
            List.of(result(41, 42), result(43, 43)),
            new SequencePosition(43)
        ).highWatermark()).isEqualTo(new SequencePosition(43));
    }

    @Test
    void returnsTheOnlyResult() {
        AppendResult result = result(41, 41);

        assertThat(new CommitOutcome(List.of(result), new SequencePosition(41)).onlyResult()).isEqualTo(result);
    }

    @Test
    void rejectsRequestingOnlyResultFromBatchOutcome() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(result(41, 41), result(42, 42)),
            new SequencePosition(42)
        ).onlyResult())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("commit outcome does not contain exactly one append result");
    }

    @Test
    void rejectsCommittedPositionGaps() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(result(41, 41), result(43, 43)),
            new SequencePosition(43)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("append result positions must be contiguous");
    }

    @Test
    void rejectsHighWatermarkDifferentFromCommittedRangeEnd() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(result(41, 42), result(43, 43)),
            new SequencePosition(44)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("high watermark must match committed append results");
    }

    @Test
    void acceptsReplayedResultBeforeCurrentHighWatermark() {
        assertThat(new CommitOutcome(
            List.of(new AppendResult(new SequencePosition(41), new SequencePosition(42), true)),
            new SequencePosition(49)
        ).highWatermark()).isEqualTo(new SequencePosition(49));
    }

    @Test
    void rejectsReplayedResultAfterCurrentHighWatermark() {
        assertThatThrownBy(() -> new CommitOutcome(
            List.of(new AppendResult(new SequencePosition(41), new SequencePosition(42), true)),
            new SequencePosition(40)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("replayed append result must not exceed high watermark");
    }

    private static AppendResult result(final long from, final long to) {
        return new AppendResult(new SequencePosition(from), new SequencePosition(to), false);
    }
}
