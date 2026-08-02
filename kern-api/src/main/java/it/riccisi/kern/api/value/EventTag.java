package it.riccisi.kern.api.value;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Logical association that lets one event participate in one or more histories.
 */
public record EventTag(String name, String value) {
    private static final int MAX_COMPONENT_BYTES = 256;

    public EventTag {
        Objects.requireNonNull(name, "event tag name must not be null");
        Objects.requireNonNull(value, "event tag value must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("event tag name must not be blank");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("event tag value must not be blank");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_COMPONENT_BYTES) {
            throw new IllegalArgumentException("event tag name must not exceed 256 UTF-8 bytes");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_COMPONENT_BYTES) {
            throw new IllegalArgumentException("event tag value must not exceed 256 UTF-8 bytes");
        }
    }

    @Override
    public String toString() {
        return name + ":" + value;
    }
}
