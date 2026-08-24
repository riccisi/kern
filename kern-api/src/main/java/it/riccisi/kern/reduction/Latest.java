package it.riccisi.kern.reduction;

import it.riccisi.kern.EventReduction;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.Position;
import lombok.NonNull;

import java.util.Objects;

/**
 * Reduction keeping only the event with the greatest {@link Position}.
 */
public final class Latest implements EventReduction {

    /**
     * Describes this reduction to an event-reduction interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This reduction represented as {@code T}.
     */
    @Override
    public <T> T describe(@NonNull final EventReductionSelection<T> selection) {
        return selection.latest();
    }
}
