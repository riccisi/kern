package it.riccisi.kern.api.query;

import java.util.Objects;

/**
 * Opaque token for continuing a bounded read.
 */
public record ContinuationToken(String value) {
    public ContinuationToken {
        Objects.requireNonNull(value, "continuation token must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("continuation token must not be blank");
        }
    }
}
