package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import java.util.Objects;

public final class EventKey extends BinaryKeyEnvelope {

    public EventKey(Namespace namespace, Position position) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.EVENT,
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
