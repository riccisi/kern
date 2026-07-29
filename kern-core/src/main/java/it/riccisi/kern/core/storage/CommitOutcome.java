package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.Position;
import java.util.List;
import java.util.Objects;

public record CommitOutcome(
    List<AppendResult> results,
    Position highWatermark
) {
    public CommitOutcome {
        results = List.copyOf(Objects.requireNonNull(results, "append results must not be null"));
        if (results.isEmpty()) {
            throw new IllegalArgumentException("append results must not be empty");
        }
        Objects.requireNonNull(highWatermark, "high watermark must not be null");
    }
}
