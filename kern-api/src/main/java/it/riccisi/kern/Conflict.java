package it.riccisi.kern;

/**
 * Explanation of a stale-tail conflict.
 */
public interface Conflict {

    /**
     * The stored event that invalidated the observation represented by a
     * {@link Tail}.
     *
     * @return The conflicting stored event.
     */
    StoredEvent event();
}
