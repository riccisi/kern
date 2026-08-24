package it.riccisi.kern;

/**
 * Immutable bounded observation of persisted events.
 *
 * <p>The observation is conceptually defined by a namespace, an
 * {@link EventFilter}, an exclusive lower {@link Position}, a hidden upper
 * watermark, and an ordered reduction pipeline. Iteration observes the current
 * representation inside that fixed window. The object is an {@link Iterable},
 * not a cursor.</p>
 */
public interface StoredEvents extends Iterable<StoredEvent> {

    /**
     * Derives a reduced representation of this same bounded observation.
     *
     * <p>Reduction may remove events from iteration, but it must not create,
     * modify, replace, or reorder surviving events. It also preserves the
     * namespace, dependency filter, hidden watermark, and therefore the
     * consistency boundary used by {@link #tail()}.</p>
     *
     * @param reduction The declarative reduction to append to this observation.
     * @return A reduced observation with the same consistency boundary.
     */
    StoredEvents reduce(EventReduction reduction);

    /**
     * Continues this observation through conditional writing.
     *
     * @return A tail bound to this observation's namespace, filter, and hidden
     *     watermark.
     */
    Tail tail();

    /**
     * Continues this observation through future reads.
     *
     * @return A subscription beginning strictly after this observation's hidden
     *     watermark.
     */
    Subscription follow();
}
