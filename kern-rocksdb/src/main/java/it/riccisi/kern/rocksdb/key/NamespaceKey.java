package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import java.util.Objects;

public record NamespaceKey(Namespace namespace) implements BinaryKey {
    public NamespaceKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .bytes();
    }
}
