package it.riccisi.kern.core.append;

import it.riccisi.kern.api.value.Position;
import java.util.Objects;

final class AppendPositionRange {
    private final Position highWatermark;
    private final int events;

    AppendPositionRange(final Position highWatermark, final int events) {
        this.highWatermark = Objects.requireNonNull(highWatermark, "high watermark must not be null");
        if (events <= 0) {
            throw new IllegalArgumentException("events must be positive");
        }
        this.events = events;
    }

    Position from() {
        return highWatermark.next();
    }

    Position to() {
        long from = from().value();
        long to = Math.addExact(from, events - 1L);
        return new Position(to);
    }
}
