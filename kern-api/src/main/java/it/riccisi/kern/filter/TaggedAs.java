package it.riccisi.kern.filter;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventSelection;
import it.riccisi.kern.tag.EventTag;
import it.riccisi.kern.Tag;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * Leaf filter selecting events associated with a tag.
 */
@RequiredArgsConstructor
public final class TaggedAs implements EventFilter {

    @NonNull private final Tag tag;

    /**
     * Builds a tag filter from raw name and value strings.
     *
     * @param name The required tag name.
     * @param value The required tag value.
     */
    public TaggedAs(@NonNull final String name, @NonNull final String value) {
        this(new EventTag(name, value));
    }

    /**
     * Describes this tag filter to an event-selection interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This filter represented as {@code T}.
     */
    @Override
    public <T> T describe(@NonNull final EventSelection<T> selection) {
        return selection.taggedAs(this.tag);
    }
}
