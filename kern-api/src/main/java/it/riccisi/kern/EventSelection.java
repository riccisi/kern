package it.riccisi.kern;

/**
 * Interpretation boundary for {@link EventFilter} descriptions.
 *
 * <p>The same filter algebra can be interpreted as in-memory predicates,
 * storage indexes, protocol messages, diagnostics, or another representation
 * without changing the public filter objects.</p>
 *
 * @param <T> The representation produced by this interpreter.
 */
public interface EventSelection<T> {

    /**
     * Represents events matching all supplied selections.
     *
     * @param selections The selections to combine.
     * @return The combined selection.
     */
    T all(Iterable<T> selections);

    /**
     * Represents events matching any supplied selection.
     *
     * @param selections The selections to combine.
     * @return The combined selection.
     */
    T any(Iterable<T> selections);

    /**
     * Represents events of a given type.
     *
     * @param type The required event type.
     * @return The typed selection.
     */
    T typedBy(EventType type);

    /**
     * Represents events associated with a given tag.
     *
     * @param tag The required event tag.
     * @return The tagged selection.
     */
    T taggedAs(Tag tag);
}
