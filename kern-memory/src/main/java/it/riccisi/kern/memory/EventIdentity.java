package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Verifies that two events with the same {@code EventId} describe the same
 * semantic fact.
 */
@RequiredArgsConstructor
final class EventIdentity {

    @NonNull private final Event event;

    void verify(final Event other) {
        if (
            !this.event.type().equals(other.type())
                || !new TagsIdentity(this.event.tags()).matches(other.tags())
                || !new DataIdentity(this.event.data()).matches(other.data())
        ) {
            throw new IllegalArgumentException("EventId identifies a different event");
        }
    }
}
