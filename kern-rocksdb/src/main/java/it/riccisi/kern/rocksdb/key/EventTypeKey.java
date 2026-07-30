package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class EventTypeKey extends BinaryKeyEnvelope {

    public EventTypeKey(final Namespace namespace, final EventType type, final Position position) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.TYPE,
                new TextBytes(Objects.requireNonNull(type, "event type must not be null").value()),
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
