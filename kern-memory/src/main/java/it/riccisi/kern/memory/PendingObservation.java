package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvents;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Pending demand for the next non-empty subscription observation.
 *
 * <p>The object checks the authoritative memory history and represents the
 * result as a {@link CurrentObservation}. It does not complete itself while the
 * namespace lock is held.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PendingObservation extends CompletableFuture<StoredEvents> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7752223192610629067L;

    @NonNull private final MemoryNamespace namespace;
    @NonNull private final EventLog events;
    @NonNull private final EventFilter filter;
    @NonNull private final Position watermark;

    private final int count;

    /**
     * Checks whether this demand can be completed now.
     *
     * @return A ready or empty current observation.
     */
    CurrentObservation check() {
        final Position head = this.events.head();
        final CurrentObservation observation;
        if (this.events.hasObservation(
            this.filter,
            this.watermark,
            head,
            this.count
        )) {
            observation = new ReadyObservation(
                this,
                this.namespace.observation(
                    this.filter,
                    this.watermark,
                    this.events.watermark(
                        this.filter,
                        this.watermark,
                        head,
                        this.count
                    )
                )
            );
        } else {
            observation = new EmptyObservation(this);
        }
        return observation;
    }
}
