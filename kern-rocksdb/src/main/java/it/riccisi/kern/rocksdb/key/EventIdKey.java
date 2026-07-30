package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.UuidBytes;
import java.util.Objects;

public final class EventIdKey extends BinaryKeyEnvelope {

    public EventIdKey(Namespace namespace, EventId id) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.EVENT_ID,
                new UuidBytes(Objects.requireNonNull(id, "event id must not be null").value())
            )
        );
    }
}
