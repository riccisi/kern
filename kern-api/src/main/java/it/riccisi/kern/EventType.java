package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Semantic kind of fact represented by an {@link Event}.
 *
 * <p>The event type answers what happened. It is distinct from
 * {@link Metadata}, which describes the structure of the event data.</p>
 */
public final class EventType implements Text {

    private final String origin;

    /**
     * Builds an event type.
     *
     * @param origin The event type text.
     * @throws NullPointerException     When {@code origin} is {@code null}.
     * @throws IllegalArgumentException When {@code origin} is blank.
     */
    public EventType(@NonNull final String origin) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException("EventType must not be blank");
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof EventType that
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
