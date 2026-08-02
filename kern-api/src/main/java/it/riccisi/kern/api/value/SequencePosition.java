package it.riccisi.kern.api.value;

/**
 * Monotonic position assigned by the authoritative global event log.
 */
public record SequencePosition(long value) {

    public SequencePosition {
        if (value < 0) {
            throw new IllegalArgumentException("sequence position must not be negative");
        }
    }

    public SequencePosition next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("sequence position cannot advance beyond Long.MAX_VALUE");
        }
        return new SequencePosition(value + 1);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
