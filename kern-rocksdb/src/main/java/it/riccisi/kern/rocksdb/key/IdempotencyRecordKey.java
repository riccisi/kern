package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class IdempotencyRecordKey extends BinaryKeyEnvelope {

    public IdempotencyRecordKey(final Namespace namespace, final IdempotencyKey key) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.IDEMPOTENCY,
                new TextBytes(Objects.requireNonNull(key, "idempotency key must not be null").value())
            )
        );
    }
}
