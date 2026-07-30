package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.IntBytes;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.cactoos.Bytes;

final class EventRecordTags extends BytesEnvelope {

    EventRecordTags(final Map<String, String> tags) {
        super(encoded(tags));
    }

    private static Bytes encoded(final Map<String, String> tags) {
        Map<String, String> sorted = new TreeMap<>(Objects.requireNonNull(tags, "event tags must not be null"));
        List<Bytes> parts = new ArrayList<>();
        parts.add(new IntBytes(sorted.size()));
        for (Map.Entry<String, String> tag : sorted.entrySet()) {
            parts.add(new TextBytes(tag.getKey()));
            parts.add(new TextBytes(tag.getValue()));
        }
        return new JoinedBytes(parts);
    }
}
