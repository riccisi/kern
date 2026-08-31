package it.riccisi.kern.memory;

/**
 * Result of checking an awaiting subscription observation.
 *
 * <p>A current observation may either represent no available events, or a
 * bounded observation ready to complete a subscription. Registration decisions
 * happen while the namespace lock is held; delivery may happen later, outside
 * the critical section.</p>
 */
interface CurrentObservation {

    void manage(PendingObservations observations);
    /**
     * Delivers this observation.
     */
    void deliver();
}
