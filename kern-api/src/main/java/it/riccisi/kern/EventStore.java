package it.riccisi.kern;

/**
 * Long-lived semantic capability for observing persisted events.
 *
 * <p>The store is addressed by {@link Namespace}. Each observation returns a
 * bounded {@link StoredEvents} object whose continuations preserve the exact
 * upper boundary of that observation.</p>
 */
public interface EventStore {

    /**
     * Observes matching stored events in a namespace.
     *
     * <p>The returned observation contains matching events strictly after
     * {@code after} and up to a consistent hidden watermark captured when the
     * observation is created.</p>
     *
     * @param namespace The logical event log to observe.
     * @param filter The semantic relevance boundary.
     * @param after The exclusive lower position boundary.
     * @return A bounded observation of matching persisted events.
     */
    StoredEvents events(Namespace namespace, EventFilter filter, Position after);

    /**
     * Observes matching stored events in a namespace from the beginning.
     *
     * @param namespace The logical event log to observe.
     * @param filter The semantic relevance boundary.
     * @return A bounded observation of matching persisted events.
     */
    default StoredEvents events(final Namespace namespace, final EventFilter filter) {
        return this.events(namespace, filter, Position.beginning());
    }

    /**
     * Observes matching stored events in the default namespace from the
     * beginning.
     *
     * @param filter The semantic relevance boundary.
     * @return A bounded observation of matching persisted events.
     */
    default StoredEvents events(final EventFilter filter) {
        return this.events(Namespace.DEFAULT, filter);
    }
}
