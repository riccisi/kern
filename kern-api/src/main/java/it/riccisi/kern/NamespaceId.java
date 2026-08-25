package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Identifier of a logical event partition addressed by {@link EventStore}
 * operations.
 *
 * <p>A namespace id scopes observations, positions, tails, conflicts,
 * idempotency, and subscriptions. It is not an event tag or an event
 * property.</p>
 */
public final class NamespaceId implements Text {

    /**
     * Default logical event log identifier.
     */
    public static final NamespaceId DEFAULT = new NamespaceId("default");

    private final String origin;

    /**
     * Builds a namespace id.
     *
     * @param origin The namespace id text.
     * @throws NullPointerException     When {@code origin} is {@code null}.
     * @throws IllegalArgumentException When {@code origin} is blank.
     */
    public NamespaceId(@NonNull final String origin) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException("NamespaceId must not be blank");
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof NamespaceId that
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
