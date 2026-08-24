package it.riccisi.kern.tag;

import it.riccisi.kern.Tag;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import lombok.NonNull;

/**
 * Immutable {@link Tag} made of a name and a value.
 *
 * @param name The tag name.
 * @param value The tag value.
 */
public record EventTag(@NonNull TagName name, @NonNull TagValue value) implements Tag {

    /**
     * Builds a tag from raw name and value strings.
     *
     * @param name  The tag name.
     * @param value The tag value.
     */
    public EventTag(@NonNull final String name, @NonNull final String value) {
        this(new TagName(name), new TagValue(value));
    }


    @Override
    public String toString() {
        return this.name + "=" + this.value;
    }
}
