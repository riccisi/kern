package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Name of an application-defined event tag.
 */
public final class TagName implements Text {

    private final String origin;

    /**
     * Builds a tag name.
     *
     * @param origin The tag name text.
     * @throws NullPointerException     When {@code origin} is {@code null}.
     * @throws IllegalArgumentException When {@code origin} is blank.
     */
    public TagName(@NonNull final String origin) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException("TagName must not be blank");
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof TagName that
            && this.origin.equals(that.origin);
    }

    @Override
    public int hashCode() {
        return this.origin.hashCode();
    }

    @Override
    public String toString() {
        return this.origin;
    }
}
