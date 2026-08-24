package it.riccisi.kern;

import lombok.NonNull;

/**
 * Intrinsic identity of an {@link Event}.
 *
 * <p>The identity exists before persistence and is part of the semantic event
 * fact. It supports stable identification, deduplication, and idempotent retry
 * of appends.</p>
 *
 * @param value The identity value.
 */
public record EventId(@NonNull String value) {

    /**
     * Builds an event identity.
     *
     * @param value The identity value.
     * @throws NullPointerException     When {@code value} is {@code null}.
     * @throws IllegalArgumentException When {@code value} is blank.
     */
    public EventId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("EventId must not be blank");
        }
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof EventId that
            && this.value.equals(that.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
