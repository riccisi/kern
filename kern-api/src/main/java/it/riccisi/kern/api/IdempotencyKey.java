package it.riccisi.kern.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record IdempotencyKey(String value) {
    private static final int MAX_BYTES = 256;

    public IdempotencyKey {
        Objects.requireNonNull(value, "idempotency key must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("idempotency key must not exceed 256 UTF-8 bytes");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
