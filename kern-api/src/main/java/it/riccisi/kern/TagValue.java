package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Canonical value of an application-defined event tag.
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
