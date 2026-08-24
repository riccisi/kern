package it.riccisi.kern;

/**
 * A point in a namespace-local logical event log.
 *
 * <p>Positions provide stable total ordering of stored events. They may
 * represent logical points that are not occupied by events, such as
 * {@link #beginning()}, and clients must not rely on numeric contiguity.</p>
 *
 * @param value The position value.
 */
public record Position(long value) implements Comparable<Position> {

    private static final Position BEGINNING = new Position(0L);

    /**
     * Builds a position.
     *
     * @param value The position value.
     * @throws IllegalArgumentException When {@code value} is negative.
     */
    public Position {
        if (value < 0L) {
            throw new IllegalArgumentException("Position must not be negative");
        }
    }

    /**
     * The logical point immediately before the first possible stored event.
     *
     * @return The beginning position.
     */
    public static Position beginning() {
        return Position.BEGINNING;
    }

    /**
     * Compares positions by their stable log ordering.
     *
     * @param other The position to compare with.
     * @return A negative value, zero, or a positive value according to ordering.
     */
    @Override
    public int compareTo(final Position other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof Position that
            && this.value == that.value;
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
