package it.riccisi.kern;

import lombok.NonNull;

/**
 * Canonical value of an application-defined event tag.
 *
 * @param value The tag value.
 */
public record TagValue(@NonNull String value) {

    /**
     * Builds a tag value.
     *
     * @param value The tag value.
     * @throws NullPointerException     When {@code value} is {@code null}.
     * @throws IllegalArgumentException When {@code value} is blank.
     */
    public TagValue {
        if (value.isBlank()) {
            throw new IllegalArgumentException("TagValue must not be blank");
        }
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof TagValue that
            && this.value.equals(that.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
