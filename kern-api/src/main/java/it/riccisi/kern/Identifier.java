package it.riccisi.kern;

import java.util.regex.Pattern;
import lombok.NonNull;
import org.cactoos.Text;

final class Identifier implements Text {

    private static final Pattern VALID =
        Pattern.compile("[A-Za-z][A-Za-z0-9._-]*");

    private final String origin;

    Identifier(
        @NonNull final String origin,
        final int maximum,
        final String label
    ) {
        this(
            origin,
            maximum,
            label,
            Identifier.VALID
        );
    }

    Identifier(
        @NonNull final String origin,
        final int maximum,
        final String label,
        @NonNull final Pattern pattern
    ) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException(
                label + " must not be blank"
            );
        }
        if (!origin.equals(origin.strip())) {
            throw new IllegalArgumentException(
                label + " must not have surrounding whitespace"
            );
        }
        if (origin.length() > maximum) {
            throw new IllegalArgumentException(
                label + " must not exceed "
                    + maximum
                    + " characters"
            );
        }
        if (!pattern.matcher(origin).matches()) {
            throw new IllegalArgumentException(
                label + " must match "
                    + pattern.pattern()
            );
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }
}
