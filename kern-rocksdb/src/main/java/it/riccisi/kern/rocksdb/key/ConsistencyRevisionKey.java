package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Namespace;
import java.util.Objects;

public record ConsistencyRevisionKey(Namespace namespace, ConsistencyKey key) implements BinaryKey {
    public ConsistencyRevisionKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(key, "consistency key must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.CONSISTENCY)
            .text(key.value())
            .bytes();
    }
}
