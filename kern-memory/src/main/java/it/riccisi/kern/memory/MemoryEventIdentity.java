package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MemoryEventIdentity {

    @NonNull private final Event event;

    void verify(final Event other) {
        if (
            !this.event.type().equals(other.type())
                || !this.event.tags().equals(other.tags())
                || !Objects.equals(this.event.data(), other.data())
        ) {
            throw new IllegalArgumentException("EventId identifies a different event");
        }
    }
}
