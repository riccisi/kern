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
