package it.riccisi.kern;

import java.util.regex.Pattern;
import lombok.NonNull;

/**
 * Identifier of a logical event partition addressed by {@link EventStore}
 * operations.
 *
 * <p>A namespace id scopes observations, positions, tails, conflicts,
 * idempotency, and subscriptions. It is not an event tag or an event
 * property. Its portable identifier format is
 * {@code [A-Za-z0-9][A-Za-z0-9._-]*}.</p>
 */
public final class NamespaceId extends SemanticAtom {

    private static final Pattern VALID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static final int MAXIMUM_LENGTH = 128;

    public static final NamespaceId DEFAULT = new NamespaceId("default");

    public NamespaceId(@NonNull final String origin) {
        super(
            new Identifier(
                origin,
                NamespaceId.MAXIMUM_LENGTH,
                "NamespaceId",
                NamespaceId.VALID
            )
        );
    }
}
