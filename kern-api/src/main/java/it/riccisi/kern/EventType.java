package it.riccisi.kern;

import lombok.NonNull;

/**
 * Semantic kind of fact represented by an {@link Event}.
 *
 * <p>The event type answers what happened. It is distinct from
 * {@link Metadata}, which describes the structure of the event data.</p>
 *
 * @param value The event type value.
 */
public record EventType(@NonNull String value) {

    /**
     * Builds an event type.
     *
     * @param value The event type value.
     * @throws NullPointerException     When {@code value} is {@code null}.
     * @throws IllegalArgumentException When {@code value} is blank.
     */
    public EventType {
        if (value.isBlank()) {
            throw new IllegalArgumentException("EventType must not be blank");
        }
    }

    @Override
    public String toString() {
        return this.value;
    }
}
