package it.riccisi.kern;

/**
 * Declarative description of which events are relevant to an observation.
 *
 * <p>A filter describes itself through an {@link EventSelection} so each
 * implementation can translate the same semantic filter into its own native
 * representation without inspecting filter internals procedurally.</p>
 */
public interface EventFilter {

    /**
     * Describes this filter to an event-selection interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This filter represented as {@code T}.
     */
    <T> T describe(EventSelection<T> selection);
}
