package it.riccisi.kern;

import java.time.Instant;

/**
 * An {@link Event} after it has been recorded in Kern.
 *
 * <p>Persistence enriches the original event with a stable log position and a
 * storage time. The original event identity, type, tags, and data remain the
 * same semantic fact.</p>
 */
public interface StoredEvent extends Event {

    /**
     * The point occupied by this event in its namespace-local logical log.
     *
     * @return The stored event position.
     */
    Position position();

    /**
     * The time at which this event was recorded.
     *
     * @return The storage time.
     */
    Instant storedAt();
}
