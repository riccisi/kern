package it.riccisi.kern.api.value;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record Subject(String value) {
    private static final int MAX_BYTES = 512;

    public Subject {
        Objects.requireNonNull(value, "subject must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("subject must not exceed 512 UTF-8 bytes");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
