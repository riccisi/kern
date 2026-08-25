package it.riccisi.kern;

import lombok.NonNull;

/**
 * Canonical value of an application-defined event tag.
 *
 * <p>A tag value is an opaque bounded textual value, not a schema-constrained
 * identifier.</p>
 */
public final class TagValue extends SemanticAtom {

    private static final int MAXIMUM_BYTES = 1024;

    public TagValue(@NonNull final String origin) {
        super(
            new BoundedText(
                origin,
                TagValue.MAXIMUM_BYTES,
                "TagValue"
            )
        );
    }
}
