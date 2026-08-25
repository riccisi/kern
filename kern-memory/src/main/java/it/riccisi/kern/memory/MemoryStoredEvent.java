package it.riccisi.kern.memory;

import it.riccisi.kern.Data;
import it.riccisi.kern.Event;
import it.riccisi.kern.EventId;
import it.riccisi.kern.EventType;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tags;
import java.time.Instant;
import lombok.NonNull;

record MemoryStoredEvent(
    @NonNull Event event,
    @NonNull Position position,
    @NonNull Instant storedAt
) implements StoredEvent {

    @Override
    public EventId id() {
        return this.event.id();
    }

    @Override
    public EventType type() {
        return this.event.type();
    }

    @Override
    public Tags tags() {
        return this.event.tags();
    }

    @Override
    public Data data() {
        return this.event.data();
    }
}
