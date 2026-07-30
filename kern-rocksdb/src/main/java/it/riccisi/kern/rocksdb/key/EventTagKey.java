package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class EventTagKey extends BinaryKeyEnvelope {

    public EventTagKey(final Namespace namespace, final String name, final String value, final Position position) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.TAG,
                new TextBytes(Objects.requireNonNull(name, "tag name must not be null")),
                new TextBytes(Objects.requireNonNull(value, "tag value must not be null")),
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
