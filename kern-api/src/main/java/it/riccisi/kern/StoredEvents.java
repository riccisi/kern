package it.riccisi.kern;

/**
 * Immutable bounded observation of persisted events.
 *
 * <p>The observation is conceptually defined by a namespace, an
 * {@link EventFilter}, an exclusive lower {@link Position}, and a hidden upper
 * watermark. Iteration observes only matching events inside that fixed window.
 * The object is an {@link Iterable}, not a cursor.</p>
 */
public interface StoredEvents extends Iterable<StoredEvent> {

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
