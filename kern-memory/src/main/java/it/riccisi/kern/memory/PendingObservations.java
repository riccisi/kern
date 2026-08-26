package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.iterable.Mapped;
import org.cactoos.list.ListOf;

/**
 * Awaiting subscription observations for one namespace.
 *
 * <p>The registry decides, under the namespace lock, which demands must remain
 * awaiting and which bounded observations are ready. The returned observations
 * are delivered outside the critical section.</p>
 */
@RequiredArgsConstructor
final class PendingObservations {

    @NonNull private final EventLog events;

    private final List<PendingObservation> observations = new ArrayList<>();

    CompletionStage<StoredEvents> next(
        final MemoryNamespace namespace,
        final EventFilter filter,
        final Position watermark,
        final int count
    ) {
        final PendingObservation observation = new PendingObservation(
            namespace,
            this.events,
            filter,
            watermark,
            count
        );
        final CurrentObservation current = observation.check();
        current.manage(this);
        current.deliver();
        return observation;
    }

    Iterable<CurrentObservation> check() {
        return new ListOf<>(
            new Mapped<CurrentObservation>(
                pendingObservation -> {
                    final CurrentObservation current = pendingObservation.check();
                    current.manage(this);
                    return current;
                },
                List.copyOf(this.observations)
            )
        );
    }

    void await(final PendingObservation observation) {
        if (!this.observations.contains(observation)) {
            this.observations.add(observation);
        }
    }

    void remove(final PendingObservation observation) {
        this.observations.remove(observation);
    }
}
