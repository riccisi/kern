package it.riccisi.kern;

import lombok.NonNull;

/**
 * Name of an application-defined event tag.
 *
 * <p>Its portable identifier format is
 * {@code [A-Za-z][A-Za-z0-9._-]*}.</p>
 */
public final class TagName extends SemanticAtom {

    private static final int MAXIMUM_LENGTH = 64;

    public TagName(@NonNull final String origin) {
        super(
            new Identifier(
                origin,
                TagName.MAXIMUM_LENGTH,
                "TagName"
            )
        );
    }
}
