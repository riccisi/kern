package it.riccisi.kern.api.value;

public record Position(long value) {
    public Position {
        if (value < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
    }

    public Position next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("position cannot advance beyond Long.MAX_VALUE");
        }
        return new Position(value + 1);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
