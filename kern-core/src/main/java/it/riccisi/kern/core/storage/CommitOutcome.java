package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.List;
import java.util.Objects;

public record CommitOutcome(
    List<AppendResult> results,
    SequencePosition highWatermark
) {
    public CommitOutcome {
        results = List.copyOf(Objects.requireNonNull(results, "append results must not be null"));
        if (results.isEmpty()) {
            throw new IllegalArgumentException("append results must not be empty");
        }
        Objects.requireNonNull(highWatermark, "high watermark must not be null");
        long expected = results.getFirst().fromPosition().value();
        long last = -1;
        boolean exhausted = false;
        for (AppendResult result : results) {
            if (exhausted) {
                throw new IllegalArgumentException("append result positions must be contiguous");
            }
            if (result.fromPosition().value() != expected) {
                throw new IllegalArgumentException("append result positions must be contiguous");
            }
            last = result.toPosition().value();
            if (last == Long.MAX_VALUE) {
                expected = Long.MAX_VALUE;
                exhausted = true;
            } else {
                expected = last + 1;
            }
        }
        if (highWatermark.value() != last) {
            throw new IllegalArgumentException("high watermark must match committed append results");
        }
    }

    public AppendResult onlyResult() {
        if (results.size() != 1) {
            throw new IllegalStateException("commit outcome does not contain exactly one append result");
        }
        return results.getFirst();
    }
}
