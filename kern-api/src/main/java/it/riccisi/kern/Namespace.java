package it.riccisi.kern;

import lombok.NonNull;

/**
 * Logical event partition addressed by {@link EventStore} operations.
 *
 * <p>A namespace scopes observations, positions, tails, conflicts,
 * idempotency, and subscriptions. It is not an event tag or an event property.</p>
 *
 * @param value The namespace value.
 */
public record Namespace(@NonNull String value) {

    /**
     * Default logical event log.
     */
    public static final Namespace DEFAULT = new Namespace("default");

    /**
     * Builds a namespace.
     *
     * @param value The namespace value.
     * @throws NullPointerException     When {@code value} is {@code null}.
     * @throws IllegalArgumentException When {@code value} is blank.
     */
    public Namespace(final String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Namespace must not be blank");
        }
        this.value = value;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof Namespace that
            && this.value.equals(that.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
