package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvents;
import it.riccisi.kern.Subscription;
import java.util.concurrent.CompletionStage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Immutable continuation from an in-memory observation watermark.
 */
@RequiredArgsConstructor
final class MemorySubscription implements Subscription {

    @NonNull private final MemoryNamespace namespace;

    @NonNull private final EventFilter filter;

    @NonNull private final Position watermark;

    @Override
    public CompletionStage<StoredEvents> next(final int count) {
        return this.namespace.next(this.filter, this.watermark, count);
    }
}
