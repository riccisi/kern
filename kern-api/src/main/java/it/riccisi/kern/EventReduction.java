package it.riccisi.kern;

/**
 * Declarative reduction of a bounded {@link StoredEvents} observation.
 *
 * <p>A reduction derives a smaller ordered representation of the same
 * observation. It may discard events, but it does not change the observation's
 * namespace, dependency filter, hidden watermark, or tail consistency
 * boundary.</p>
 */
public interface EventReduction {

    /**
     * Describes this reduction to an event-reduction interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This reduction represented as {@code T}.
     */
    <T> T describe(EventReductionSelection<T> selection);
}
