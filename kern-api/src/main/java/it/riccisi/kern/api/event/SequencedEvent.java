package it.riccisi.kern.api.event;

import it.riccisi.kern.api.value.SequencePosition;
import java.time.Instant;
import java.util.Objects;

/**
 * Event observed at one global sequence position.
 */
public record SequencedEvent(
    SequencePosition position,
    Instant recordedAt,
    EventData event
) {
    public SequencedEvent {
        Objects.requireNonNull(position, "sequence position must not be null");
        Objects.requireNonNull(recordedAt, "recorded at must not be null");
        Objects.requireNonNull(event, "event data must not be null");
    }
}
