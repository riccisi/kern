package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import java.util.Objects;

public final class EventKey extends BinaryKeyEnvelope {

    public EventKey(final Namespace namespace, final SequencePosition position) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.EVENT,
                new LongBytes(Objects.requireNonNull(position, "position must not be null").value())
            )
        );
    }
}
