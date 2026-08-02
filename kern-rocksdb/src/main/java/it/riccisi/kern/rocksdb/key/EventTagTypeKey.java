package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class EventTagTypeKey extends BinaryKeyEnvelope {

    public EventTagTypeKey(
        final Namespace namespace,
        final EventTag tag,
        final EventType type,
        final SequencePosition position
    ) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.TAG_TYPE,
                new TextBytes(Objects.requireNonNull(tag, "event tag must not be null").name()),
                new TextBytes(tag.value()),
                new TextBytes(Objects.requireNonNull(type, "event type must not be null").value()),
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
