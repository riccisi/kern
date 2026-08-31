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
import org.cactoos.BiFunc;
import org.cactoos.iterator.Sorted;
import org.cactoos.iterator.IteratorOf;
import org.cactoos.scalar.Folded;
import org.cactoos.scalar.Unchecked;

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
        return new Sorted<>(
            Comparator.comparing(StoredEvent::position),
            new Unchecked<>(
                new Folded<>(
                    new LinkedHashMap<TagValue, StoredEvent>(),
                    (folded, event) -> {
                        new TagValueOf(event, this.tag).value().ifPresent(
                            value -> folded.put(value, event)
                        );
                        return folded;
                    },
                    this.events
                )
            ).value().values().iterator()
        );
    }
}
