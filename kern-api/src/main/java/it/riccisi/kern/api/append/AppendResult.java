package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.SequencePosition;
import java.util.Objects;

/**
 * Result of one logical append in the global event log.
 */
public record AppendResult(
    SequencePosition fromPosition,
    SequencePosition toPosition,
    boolean replayedFromIdempotency
) {
    public AppendResult {
        Objects.requireNonNull(fromPosition, "from position must not be null");
        Objects.requireNonNull(toPosition, "to position must not be null");
        if (toPosition.value() < fromPosition.value()) {
            throw new IllegalArgumentException("to position must not be before from position");
        }
    }
}
