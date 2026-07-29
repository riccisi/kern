package it.riccisi.kern.api.value;

import java.util.Objects;

public record EventType(String value) {
    public EventType {
        Objects.requireNonNull(value, "event type must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("event type must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
