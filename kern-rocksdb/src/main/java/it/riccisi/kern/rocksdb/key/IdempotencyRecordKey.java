package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import java.util.Objects;

public record IdempotencyRecordKey(Namespace namespace, IdempotencyKey key) implements BinaryKey {
    public IdempotencyRecordKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(key, "idempotency key must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.IDEMPOTENCY)
            .text(key.value())
            .bytes();
    }
}
