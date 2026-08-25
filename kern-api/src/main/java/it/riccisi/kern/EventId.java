package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Intrinsic identity of an {@link Event}.
 *
 * <p>The identity exists before persistence and is part of the semantic event
 * fact. It supports stable identification, deduplication, and idempotent retry
 * of appends.</p>
 */
public final class EventId implements Text {

    private final String origin;

    /**
     * Builds an event identity.
     *
     * @param origin The identity text.
     * @throws NullPointerException     When {@code origin} is {@code null}.
     * @throws IllegalArgumentException When {@code origin} is blank.
     */
    public EventId(@NonNull final String origin) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException("EventId must not be blank");
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof EventId that
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
