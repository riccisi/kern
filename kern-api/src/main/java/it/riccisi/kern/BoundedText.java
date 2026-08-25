package it.riccisi.kern;

import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import org.cactoos.Text;

final class BoundedText implements Text {

    private final String origin;

    BoundedText(
        @NonNull final String origin,
        final int maximum,
        final String label
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
        if (origin.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                label + " must not contain control characters"
            );
        }
        if (
            origin.getBytes(StandardCharsets.UTF_8).length
                > maximum
        ) {
            throw new IllegalArgumentException(
                label + " must not exceed "
                    + maximum
                    + " UTF-8 bytes"
            );
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }
}
