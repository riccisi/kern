package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReduction;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.Subscription;
import it.riccisi.kern.Tail;
import java.util.Iterator;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Bounded in-memory observation of namespace history.
 *
 * <p>The observation captures the original filter, exclusive lower boundary,
 * and hidden watermark. Iteration reads only the fixed bounded window, while
 * {@link #tail()} and {@link #follow()} continue from the captured watermark.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MemoryObservation implements StoredEvents {

    @NonNull private final MemoryNamespace namespace;
    @NonNull private final EventLog events;
    @NonNull private final EventFilter filter;
    @NonNull private final Position after;
    @NonNull private final Position watermark;

    @Override
    public StoredEvents reduce(@NonNull final EventReduction reduction) {
        return new MemoryReducedObservation(
            this,
            reduction
        );
    }

    @Override
    public Tail tail() {
        return new MemoryTail(this.namespace, this.filter, this.watermark);
    }

    @Override
    public Subscription follow() {
        return new MemorySubscription(this.namespace, this.filter, this.watermark);
    }

    @Override
    public Iterator<StoredEvent> iterator() {
        return this.events.observed(
            this.filter,
            this.after,
            this.watermark
        ).iterator();
    }
}
