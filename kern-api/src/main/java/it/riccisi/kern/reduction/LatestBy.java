package it.riccisi.kern.reduction;

import it.riccisi.kern.EventReduction;
import it.riccisi.kern.EventReductionSelection;
import it.riccisi.kern.TagName;
import lombok.NonNull;

import java.util.Objects;

/**
 * Reduction keeping the latest event for every distinct value of a tag.
 *
 * @param tag The tag name used to group events.
 */
public record LatestBy(@NonNull TagName tag) implements EventReduction {

    /**
     * Describes this reduction to an event-reduction interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This reduction represented as {@code T}.
     */
    @Override
    public <T> T describe(@NonNull final EventReductionSelection<T> selection) {
        return selection.latestBy(this.tag);
    }
}
