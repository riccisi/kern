package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.Tail;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Append capability tied to an in-memory observation boundary.
 */
@RequiredArgsConstructor
final class MemoryTail implements Tail {

    @NonNull private final MemoryNamespace namespace;

    @NonNull private final EventFilter filter;

    @NonNull private final Position watermark;

    @Override
    public void append(@NonNull final Iterable<? extends Event> events) {
        this.namespace.append(this.filter, this.watermark, events);
    }
}
