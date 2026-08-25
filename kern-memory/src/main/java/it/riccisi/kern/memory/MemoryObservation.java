package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReduction;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.Subscription;
import it.riccisi.kern.Tail;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.NonNull;

final class MemoryObservation implements StoredEvents {

    private final MemoryNamespace namespace;
    private final EventFilter filter;
    private final Position after;
    private final Position watermark;
    private final List<EventReduction> reductions;

    MemoryObservation(
        @NonNull final MemoryNamespace namespace,
        @NonNull final EventFilter filter,
        @NonNull final Position after,
        @NonNull final Position watermark,
        @NonNull final List<EventReduction> reductions
    ) {
        this.namespace = namespace;
        this.filter = filter;
        this.after = after;
        this.watermark = watermark;
        this.reductions = List.copyOf(reductions);
    }

    @Override
    public StoredEvents reduce(@NonNull final EventReduction reduction) {
        final List<EventReduction> next = new ArrayList<>(this.reductions);
        next.add(reduction);
        return new MemoryObservation(
            this.namespace,
            this.filter,
            this.after,
            this.watermark,
            next
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
        List<StoredEvent> observed = this.namespace.events(
            this.filter,
            this.after,
            this.watermark,
            Integer.MAX_VALUE
        );
        final MemoryReductionSelection selection = new MemoryReductionSelection();
        for (final EventReduction reduction : this.reductions) {
            observed = reduction.describe(selection).apply(observed);
        }
        return observed.iterator();
    }
}
