package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import java.util.Iterator;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Iterable view containing only the latest event from another iterable.
 */
@RequiredArgsConstructor
final class LatestEvent implements Iterable<StoredEvent> {

    @NonNull private final Iterable<StoredEvent> events;

    @Override
    public Iterator<StoredEvent> iterator() {
        StoredEvent latest = null;
        for (final StoredEvent event : this.events) {
            latest = event;
        }
        final List<StoredEvent> result;
        if (latest == null) {
            result = List.of();
        } else {
            result = List.of(latest);
        }
        return result.iterator();
    }
}
