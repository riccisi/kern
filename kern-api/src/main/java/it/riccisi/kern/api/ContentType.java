package it.riccisi.kern.api;

import java.util.Objects;

public record ContentType(String value) {
    public ContentType {
        Objects.requireNonNull(value, "content type must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("content type must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
