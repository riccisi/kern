package it.riccisi.kern.memory;

import it.riccisi.kern.EventReduction;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.Subscription;
import it.riccisi.kern.Tail;
import java.util.Iterator;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.func.UncheckedFunc;

/**
 * Reduced view of another stored-events observation.
 *
 * <p>Reduction changes only the visible iterable representation. Tail and
 * subscription continuation are delegated to the parent observation so the
 * original consistency boundary is preserved.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MemoryReducedObservation implements StoredEvents {

    @NonNull private final StoredEvents parent;
    @NonNull private final EventReduction reduction;

    @Override
    public StoredEvents reduce(@NonNull final EventReduction reduction) {
        return new MemoryReducedObservation(this, reduction);
    }

    @Override
    public Tail tail() {
        return this.parent.tail();
    }

    @Override
    public Subscription follow() {
        return this.parent.follow();
    }

    @Override
    public Iterator<StoredEvent> iterator() {
        return new UncheckedFunc<>(
            this.reduction.describe(new MemoryReductionSelection())
        ).apply(this.parent).iterator();
    }
}
