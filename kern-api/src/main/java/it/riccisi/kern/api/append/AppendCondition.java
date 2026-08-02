package it.riccisi.kern.api.append;

import it.riccisi.kern.api.query.EventQuery;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.Objects;

/**
 * Query-based optimistic condition for appending events.
 */
public record AppendCondition(
    EventQuery failIfEventsMatch,
    SequencePosition after
) {
    public AppendCondition {
        Objects.requireNonNull(failIfEventsMatch, "condition query must not be null");
        Objects.requireNonNull(after, "condition position must not be null");
    }
}
