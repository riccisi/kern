package it.riccisi.kern.api;

import java.util.Objects;

public record ConsistencyKey(String value) {
    public ConsistencyKey {
        Objects.requireNonNull(value, "consistency key must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("consistency key must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
