package it.riccisi.kern;

import java.util.List;

/**
 * Capability to conditionally continue an observation through writing.
 *
 * <p>A tail is derived from a {@link StoredEvents} observation and validates
 * appends against the same {@link EventFilter} and hidden watermark. Appending
 * changes the event log, but does not mutate the tail object.</p>
 */
public interface Tail {

    /**
     * Atomically appends events relative to this tail.
     *
     * <p>A normal return means all supplied events were accepted as one logical
     * batch, their relative order was preserved, stable positions were assigned,
     * and the active durability policy was satisfied. If a relevant event was
     * written after the observation represented by this tail, the operation
     * fails with {@link StaleTailException}. Exact idempotent retry of already
     * persisted events is semantically successful.</p>
     *
     * @param events The events to append as one logical batch.
     * @throws StaleTailException When this tail is stale because a relevant event was
     *     written after its observation watermark.
     */
    void append(Iterable<? extends Event> events);

    /**
     * Atomically appends events relative to this tail.
     *
     * @param events The events to append as one logical batch.
     * @throws StaleTailException When this tail is stale because a relevant event was
     *     written after its observation watermark.
     */
    default void append(final Event... events) {
        this.append(List.of(events));
    }
}
