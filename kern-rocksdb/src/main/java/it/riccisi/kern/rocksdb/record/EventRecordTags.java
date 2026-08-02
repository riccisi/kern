package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.IntBytes;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cactoos.Bytes;

final class EventRecordTags extends BytesEnvelope {

    EventRecordTags(final Set<EventTag> tags) {
        super(encoded(tags));
    }

    private static Bytes encoded(final Set<EventTag> tags) {
        List<EventTag> sorted = Objects.requireNonNull(tags, "event tags must not be null")
            .stream()
            .sorted(Comparator.comparing(EventTag::name).thenComparing(EventTag::value))
            .toList();
        List<Bytes> parts = new ArrayList<>();
        parts.add(new IntBytes(sorted.size()));
        for (EventTag tag : sorted) {
            parts.add(new TextBytes(tag.name()));
            parts.add(new TextBytes(tag.value()));
        }
        return new JoinedBytes(parts);
    }
}
