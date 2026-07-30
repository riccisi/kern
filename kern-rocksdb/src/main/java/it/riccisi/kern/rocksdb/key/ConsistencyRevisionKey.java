package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class ConsistencyRevisionKey extends BinaryKeyEnvelope {

    public ConsistencyRevisionKey(Namespace namespace, ConsistencyKey key) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.CONSISTENCY,
                new TextBytes(Objects.requireNonNull(key, "consistency key must not be null").value())
            )
        );
    }
}
