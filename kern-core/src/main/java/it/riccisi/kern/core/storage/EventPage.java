package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Position;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EventPage(
    List<StoredEvent> events,
    Optional<String> continuationToken,
    Position highWatermark
) {
    public EventPage {
        events = List.copyOf(Objects.requireNonNull(events, "stored events must not be null"));
        continuationToken = Objects.requireNonNull(continuationToken, "continuation token must not be null");
        continuationToken.ifPresent(token -> {
            if (token.isBlank()) {
                throw new IllegalArgumentException("continuation token must not be blank");
            }
        });
        Objects.requireNonNull(highWatermark, "high watermark must not be null");
    }
}
