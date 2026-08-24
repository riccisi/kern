package it.riccisi.kern.reduction;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventReduction;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.Tail;
import lombok.NonNull;

import java.util.Objects;

/**
 * Reduction keeping only events that match a filter in the current
 * representation.
 *
 * <p>The filter narrows only what iteration exposes after reduction. It does
 * not narrow the dependency filter that protects the observation's
 * {@link Tail}.</p>
 *
 * @param filter The representation filter.
 */
public record Matching(@NonNull EventFilter filter) implements EventReduction {

    /**
     * Describes this reduction to an event-reduction interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This reduction represented as {@code T}.
     */
    @Override
    public <T> T describe(@NonNull final EventReductionSelection<T> selection) {
        return selection.matching(this.filter);
    }
}
