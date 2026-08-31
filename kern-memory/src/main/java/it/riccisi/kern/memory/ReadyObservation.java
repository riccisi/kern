package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvents;
import lombok.NonNull;

/**
 * Current subscription observation ready to complete demand.
 *
 * <p>The stored-events observation is captured while the namespace lock is
 * held. Completing the future is intentionally delayed until {@link #deliver()}
 * is invoked outside that lock.</p>
 */
record ReadyObservation(
    @NonNull PendingObservation observation,
    @NonNull StoredEvents events
) implements CurrentObservation {

    @Override
    public void manage(PendingObservations observations) {
        observations.remove(this.observation);
    }

    @Override
    public void deliver() {
        this.observation.complete(this.events);
    }
}