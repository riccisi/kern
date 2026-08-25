package it.riccisi.kern;

import lombok.NonNull;

/**
 * Intrinsic identity of an {@link Event}.
 *
 * <p>The identity exists before persistence and is part of the semantic event
 * fact. It supports stable identification, deduplication, and idempotent retry
 * of appends. It is an opaque bounded textual value, not a schema-constrained
 * identifier.</p>
 */
public final class EventId extends SemanticAtom {

    private static final int MAXIMUM_BYTES = 256;

    public EventId(@NonNull final String origin) {
        super(
            new BoundedText(
                origin,
                EventId.MAXIMUM_BYTES,
                "EventId"
            )
        );
    }
}
