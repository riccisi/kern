package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.StoredEvents;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MemoryWaiter {

    @NonNull private final EventFilter filter;

    @NonNull private final Position watermark;

    private final int count;

    @NonNull private final CompletableFuture<StoredEvents> stage;

    boolean completeFrom(final MemoryNamespace namespace) {
        final List<StoredEvent> available = namespace.events(
            this.filter,
            this.watermark,
            namespace.head(),
            this.count
        );
        final boolean completed = !available.isEmpty();
        if (completed) {
            this.stage.complete(
                namespace.window(this.filter, this.watermark, this.count, available)
            );
        }
        return completed;
    }
}
