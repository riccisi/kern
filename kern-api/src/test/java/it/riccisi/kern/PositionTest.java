package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class PositionTest {

    @Test
    void ordersPositions() {
        assertThat(
            "Position must preserve persisted event ordering",
            Integer.signum(new Position(7L).compareTo(new Position(31L))),
            is(equalTo(-1))
        );
    }

    @Test
    void rejectsNegativePosition() {
        assertThat(
            "Position must reject negative coordinates",
            PositionTest.thrownBy(() -> new Position(-1L)),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void startsAtBeginningBoundary() {
        assertThat(
            "Position.beginning() must represent the logical boundary before stored events",
            Position.beginning(),
            is(equalTo(new Position(0L)))
        );
    }

    @Test
    void behavesAsNumericSemanticAtom() {
        assertThat(
            "Position must refine Number for terminal numeric representation",
            new Position(31L).longValue(),
            is(equalTo(31L))
        );
    }

    @Test
    void includesPositionsInsideRange() {
        assertThat(
            "Position must be inside a lower-exclusive and upper-inclusive range",
            new Position(7L).within(new Position(3L), new Position(11L)),
            is(true)
        );
    }

    @Test
    void excludesLowerBoundary() {
        assertThat(
            "Position range must exclude its lower boundary",
            new Position(3L).within(new Position(3L), new Position(11L)),
            is(false)
        );
    }

    @Test
    void includesUpperBoundary() {
        assertThat(
            "Position range must include its upper boundary",
            new Position(11L).within(new Position(3L), new Position(11L)),
            is(true)
        );
    }

    @Test
    void rejectsEqualRangeBoundaries() {
        assertThat(
            "Position range must reject equal boundaries",
            PositionTest.thrownBy(
                () -> new Position(7L).within(new Position(3L), new Position(3L))
            ),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsInvertedRangeBoundaries() {
        assertThat(
            "Position range must reject inverted boundaries",
            PositionTest.thrownBy(
                () -> new Position(7L).within(new Position(11L), new Position(3L))
            ),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    private static Class<? extends Throwable> thrownBy(final Executable executable) {
        Class<? extends Throwable> thrown = null;
        try {
            executable.execute();
        } catch (final Throwable failure) {
            thrown = failure.getClass();
        }
        return thrown;
    }
}
