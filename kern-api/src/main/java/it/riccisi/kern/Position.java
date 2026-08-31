package it.riccisi.kern;

import lombok.NonNull;

import java.io.Serial;

/**
 * A point in a namespace-local logical event log.
 *
 * <p>Positions provide stable total ordering of stored events. They may
 * represent logical points that are not occupied by events, such as
 * {@link #beginning()}, and clients must not rely on numeric contiguity.</p>
 */
public final class Position extends Number implements Comparable<Position> {

    @Serial
    private static final long serialVersionUID = -1840272228071949268L;

    private static final Position BEGINNING = new Position(0L);

    private final long origin;

    /**
     * Builds a position.
     *
     * @param origin The position value.
     * @throws IllegalArgumentException When {@code origin} is negative.
     */
    public Position(final long origin) {
        if (origin < 0L) {
            throw new IllegalArgumentException("Position must not be negative");
        }
        this.origin = origin;
    }

    /**
     * The logical point immediately before the first possible stored event.
     *
     * @return The beginning position.
     */
    public static Position beginning() {
        return Position.BEGINNING;
    }

    @Override
    public int intValue() {
        return Math.toIntExact(this.origin);
    }

    @Override
    public long longValue() {
        return this.origin;
    }

    @Override
    public float floatValue() {
        return this.origin;
    }

    @Override
    public double doubleValue() {
        return this.origin;
    }

    /**
     * Compares positions by their stable log ordering.
     *
     * @param other The position to compare with.
     * @return A negative value, zero, or a positive value according to ordering.
     */
    @Override
    public int compareTo(final Position other) {
        return Long.compare(this.origin, other.origin);
    }

    /**
     * Checks whether this position is inside a range.
     *
     * <p>The lower boundary is exclusive and the upper boundary is inclusive:
     * {@code lower < this <= upper}.</p>
     *
     * @param lower The exclusive lower boundary.
     * @param upper The inclusive upper boundary.
     * @return True when this position is inside the range.
     */
    public boolean within(@NonNull final Position lower, @NonNull final Position upper) {
        return this.compareTo(lower) > 0 && this.compareTo(upper) <= 0;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof Position that
            && this.origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.origin);
    }

    @Override
    public String toString() {
        return Long.toString(this.origin);
    }
}
