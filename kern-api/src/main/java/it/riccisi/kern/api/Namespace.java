package it.riccisi.kern.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

public record Namespace(String value) {
    private static final int MAX_BYTES = 128;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public Namespace {
        Objects.requireNonNull(value, "namespace must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException("namespace must be a lowercase slug");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("namespace must not exceed 128 UTF-8 bytes");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
