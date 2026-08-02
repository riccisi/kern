package it.riccisi.kern.api.query;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded event-log read over one namespace and one query.
 */
public record ReadRequest(
    Namespace namespace,
    EventQuery query,
    SequencePosition fromExclusive,
    int limit,
    Optional<ContinuationToken> continuation
) {
    public ReadRequest {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(query, "event query must not be null");
        Objects.requireNonNull(fromExclusive, "from position must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("read limit must be positive");
        }
        continuation = Objects.requireNonNull(continuation, "continuation token must not be null");
    }
}
