package it.riccisi.kern.api.value;

/**
 * Monotonic revision of a dynamic consistency key.
 */
public record ConsistencyRevision(long value) {

    public ConsistencyRevision {
        if (value < 0) {
            throw new IllegalArgumentException("consistency revision must not be negative");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
