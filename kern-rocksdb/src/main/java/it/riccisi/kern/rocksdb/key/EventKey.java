package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import java.util.Objects;

public record EventKey(Namespace namespace, Position position) implements BinaryKey {
    public EventKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.EVENT)
            .orderedLong(position.value())
            .bytes();
    }
}
