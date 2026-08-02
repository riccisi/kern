package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class EventTagKey extends BinaryKeyEnvelope {

    public EventTagKey(final Namespace namespace, final EventTag tag, final SequencePosition position) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.TAG,
                new TextBytes(Objects.requireNonNull(tag, "event tag must not be null").name()),
                new TextBytes(tag.value()),
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
