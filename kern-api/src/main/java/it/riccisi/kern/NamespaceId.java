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
public final class NamespaceId extends SemanticAtom {

    public static final NamespaceId DEFAULT = new NamespaceId("default");

    private static final int MAXIMUM_LENGTH = 128;

    public NamespaceId(@NonNull final String origin) {
        super(
            new Identifier(
                origin,
                NamespaceId.MAXIMUM_LENGTH,
                "NamespaceId"
            )
        );
    }
}