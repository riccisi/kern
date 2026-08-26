package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Iterable view containing the latest event for each value of a tag.
 *
 * <p>Events without the requested tag are ignored. Surviving events are emitted
 * in stored position order.</p>
 */
@RequiredArgsConstructor
final class LatestEventsByTag implements Iterable<StoredEvent> {

    @NonNull private final Iterable<StoredEvent> events;

    @NonNull private final TagName tag;

    @Override
    public Iterator<StoredEvent> iterator() {
        final Map<TagValue, StoredEvent> latest = new LinkedHashMap<>();
        for (final StoredEvent event : this.events) {
            new TagValueOf(event, this.tag).value().ifPresent(
                value -> latest.put(value, event)
            );
        }
        return latest.values().stream()
            .sorted(Comparator.comparing(StoredEvent::position))
            .iterator();
    }
}
