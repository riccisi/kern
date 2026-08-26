package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvents;
import java.util.concurrent.CompletionStage;
import lombok.NonNull;

/**
 * Coordination boundary for one in-memory namespace.
 *
 * <p>The namespace owns its event history and pending subscriptions. It keeps
 * append, observation, and subscription continuation decisions synchronized, but
 * delegates the concrete history and waiter behavior to dedicated objects.</p>
 */
final class MemoryNamespace {

    private final EventLog events;
    private final PendingObservations observations;

    MemoryNamespace() {
        this.events = new EventLog();
        this.observations = new PendingObservations(this.events);
    }

    synchronized StoredEvents observe(
        @NonNull final EventFilter filter,
        @NonNull final Position after
    ) {
        return this.observation(filter, after, this.events.head());
    }

    void append(
        final EventFilter filter,
        final Position watermark,
        final Iterable<? extends Event> events
    ) {
        final Iterable<CurrentObservation> observations;
        synchronized (this) {
            this.events.append(filter, watermark, events);
            observations = this.observations.check();
        }
        observations.forEach(CurrentObservation::deliver);
    }

    synchronized CompletionStage<StoredEvents> next(
        final EventFilter filter,
        final Position watermark,
        final int count
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException("Subscription demand must be positive");
        }
        return this.observations.next(
            this,
            filter,
            watermark,
            count
        );
    }

    StoredEvents observation(
        final EventFilter filter,
        final Position after,
        final Position watermark
    ) {
        return new MemoryObservation(this, this.events, filter, after, watermark);
    }
}
