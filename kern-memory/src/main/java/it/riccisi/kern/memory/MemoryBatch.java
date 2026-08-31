package it.riccisi.kern.memory;

import it.riccisi.kern.Event;
import it.riccisi.kern.EventId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.NonNull;

final class MemoryBatch implements Iterable<Event> {

    private final List<Event> events;

    MemoryBatch(@NonNull final Iterable<? extends Event> events) {
        this.events = new ArrayList<>();
        for (final Event event : events) {
            this.rejectDuplicate(event);
            this.events.add(event);
        }
    }

    int size() {
        return this.events.size();
    }

    @Override
    public Iterator<Event> iterator() {
        return this.events.iterator();
    }

    private void rejectDuplicate(final Event event) {
        final EventId identity = event.id();
        for (final Event existing : this.events) {
            if (existing.id().equals(identity)) {
                throw new IllegalArgumentException("Append batch must not contain duplicate EventIds");
            }
        }
    }
}
