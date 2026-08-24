package it.riccisi.kern;

/**
 * Interpretation boundary for {@link EventReduction} descriptions.
 *
 * <p>The same reduction algebra can be interpreted by in-memory, RocksDB,
 * remote, diagnostic, or protocol implementations without exposing arbitrary
 * Java reduction code through the public API.</p>
 *
 * @param <T> The representation produced by this interpreter.
 */
public interface EventReductionSelection<T> {

    /**
     * Represents keeping only the event with the greatest {@link Position}.
     *
     * @return The latest-event reduction.
     */
    T latest();

    /**
     * Represents keeping the latest event for each distinct value of a tag.
     *
     * @param tag The tag name used to group events.
     * @return The latest-by-tag reduction.
     */
    T latestBy(TagName tag);

    /**
     * Represents keeping only events matching a filter in the current
     * representation.
     *
     * @param filter The representation filter.
     * @return The matching reduction.
     */
    T matching(EventFilter filter);

    /**
     * Represents removing events matching a filter from the current
     * representation.
     *
     * @param filter The representation filter.
     * @return The excluding reduction.
     */
    T excluding(EventFilter filter);
}
