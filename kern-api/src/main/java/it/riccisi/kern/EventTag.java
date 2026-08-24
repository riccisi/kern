package it.riccisi.kern;

import lombok.NonNull;

/**
 * Immutable {@link Tag} made of a name and a value.
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

    /**
     * The tag name.
     *
     * @return The tag name.
     */
    @Override
    public TagName name() {
        return this.name;
    }

    /**
     * The tag value.
     *
     * @return The tag value.
     */
    @Override
    public TagValue value() {
        return this.value;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof Tag that
            && this.name.equals(that.name())
            && this.value.equals(that.value());
    }

    @Override
    public String toString() {
        return this.name + "=" + this.value;
    }
}
