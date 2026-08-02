package it.riccisi.kern.api.query;

import it.riccisi.kern.api.event.SequencedEvent;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Events returned from a snapshot and the global head observed by that snapshot.
 */
public record QueryResult(
    List<SequencedEvent> events,
    SequencePosition observedAt,
    Optional<ContinuationToken> continuation
) {
    public QueryResult {
        events = List.copyOf(Objects.requireNonNull(events, "sequenced events must not be null"));
        Objects.requireNonNull(observedAt, "observed position must not be null");
        continuation = Objects.requireNonNull(continuation, "continuation token must not be null");
    }
}
