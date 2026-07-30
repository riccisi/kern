package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class NamespaceKey extends BinaryKeyEnvelope {

    public NamespaceKey(final Namespace namespace) {
        super(
            new TextBytes(Objects.requireNonNull(namespace, "namespace must not be null").value()))
        ;
    }
}
