package it.riccisi.kern;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Leaf filter selecting events by {@link EventType}.
 */
@RequiredArgsConstructor
public final class TypedBy implements EventFilter {

    @NonNull private final EventType type;

    /**
     * Builds a type filter from a raw type string.
     *
     * @param type The required event type.
     */
    public TypedBy(@NonNull final String type) {
        this(new EventType(type));
    }

    /**
     * Describes this type filter to an event-selection interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This filter represented as {@code T}.
     */
    @Override
    public <T> T describe(final EventSelection<T> selection) {
        return selection.typedBy(this.type);
    }
}
