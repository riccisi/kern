package it.riccisi.kern.memory;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Current subscription observation with no matching events available.
 */
@RequiredArgsConstructor
final class EmptyObservation implements CurrentObservation {

    @NonNull private final PendingObservation observation;

    @Override
    public void manage(PendingObservations observations) {
        observations.await(observation);
    }

    @Override
    public void deliver() {
        // Empty observations preserve the delivery contract without branching.
    }
}