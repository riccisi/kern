package it.riccisi.kern.api.value;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {
    public EventId {
        Objects.requireNonNull(value, "event id must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
