package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import java.util.Objects;

public record EventTagKey(Namespace namespace, String name, String value, Position position) implements BinaryKey {
    public EventTagKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(name, "tag name must not be null");
        Objects.requireNonNull(value, "tag value must not be null");
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.TAG)
            .text(name)
            .text(value)
            .orderedLong(position.value())
            .bytes();
    }
}
