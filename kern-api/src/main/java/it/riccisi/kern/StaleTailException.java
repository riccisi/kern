package it.riccisi.kern;

import java.util.Objects;

/**
 * Failure raised when an append cannot use its tail because the observation is
 * stale.
 *
 * <p>A stale tail is a normal concurrency outcome: at least one event relevant
 * to the tail's original {@link EventFilter} was written after the hidden
 * watermark of the observation that produced the tail. A typical recovery is
 * to re-read, re-evaluate the domain decision, and append again.</p>
 */
public final class StaleTailException extends RuntimeException {

    /**
     * Explanation of the event that invalidated the tail.
     */
    private final Conflict conflict;

    /**
     * Builds a stale-tail failure with its conflict explanation.
     *
     * @param conflict The conflict that invalidated the tail.
     */
    public StaleTailException(final Conflict conflict) {
        super("Tail is stale");
        this.conflict = Objects.requireNonNull(conflict, "Conflict must not be null");
    }

    /**
     * The conflict that invalidated the tail.
     *
     * @return The conflict explanation.
     */
    public Conflict conflict() {
        return this.conflict;
    }
}
