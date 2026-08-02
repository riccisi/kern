package it.riccisi.kern.api.error;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.Objects;
import java.util.Set;

/**
 * Conflict caused by an event that matched an append condition after observation.
 */
public final class QueryConflict extends EventStoreException {
    private final SequencePosition observedAt;
    private final SequencePosition conflictingPosition;
    private final EventId conflictingEventId;
    private final EventType conflictingType;
    private final Set<EventTag> conflictingTags;
    private final int matchedQueryItem;

    public QueryConflict(
        final String diagnosticId,
        final SequencePosition observedAt,
        final SequencePosition conflictingPosition,
        final EventId conflictingEventId,
        final EventType conflictingType,
        final Set<EventTag> conflictingTags,
        final int matchedQueryItem
    ) {
        super(diagnosticId, "query condition was invalidated by a later matching event");
        this.observedAt = Objects.requireNonNull(observedAt, "observed position must not be null");
        this.conflictingPosition = Objects.requireNonNull(conflictingPosition, "conflicting position must not be null");
        this.conflictingEventId = Objects.requireNonNull(conflictingEventId, "conflicting event id must not be null");
        this.conflictingType = Objects.requireNonNull(conflictingType, "conflicting event type must not be null");
        this.conflictingTags = Set.copyOf(Objects.requireNonNull(conflictingTags, "conflicting tags must not be null"));
        this.matchedQueryItem = matchedQueryItem;
    }

    public SequencePosition observedAt() {
        return observedAt;
    }

    public SequencePosition conflictingPosition() {
        return conflictingPosition;
    }

    public EventId conflictingEventId() {
        return conflictingEventId;
    }

    public EventType conflictingType() {
        return conflictingType;
    }

    public Set<EventTag> conflictingTags() {
        return conflictingTags;
    }

    public int matchedQueryItem() {
        return matchedQueryItem;
    }
}
