package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class PositionTest {

    @Test
    void ordersPositions() {
        assertEquals(-1, Integer.signum(new Position(7L).compareTo(new Position(31L))));
    }

    @Test
    void rejectsNegativePosition() {
        assertThrows(IllegalArgumentException.class, () -> new Position(-1L));
    }
}
