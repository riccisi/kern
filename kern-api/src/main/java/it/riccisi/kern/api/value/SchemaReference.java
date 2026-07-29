package it.riccisi.kern.api.value;

import java.util.Objects;

public record SchemaReference(String value) {
    public SchemaReference {
        Objects.requireNonNull(value, "schema reference must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("schema reference must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
