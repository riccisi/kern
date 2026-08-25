package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Name of an application-defined event tag.
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
