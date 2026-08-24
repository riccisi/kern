package it.riccisi.kern;

import lombok.NonNull;

/**
 * Name of an application-defined event tag.
 *
 * @param value The tag name value.
 */
public record TagName(@NonNull String value) {

    /**
     * Builds a tag name.
     *
     * @param value The tag name value.
     * @throws NullPointerException     When {@code value} is {@code null}.
     * @throws IllegalArgumentException When {@code value} is blank.
     */
    public TagName {
        if (value.isBlank()) {
            throw new IllegalArgumentException("TagName must not be blank");
        }
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof TagName that
            && this.value.equals(that.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
