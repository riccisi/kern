package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import java.util.Objects;

public record EventTypeKey(Namespace namespace, EventType type, Position position) implements BinaryKey {
    public EventTypeKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(type, "event type must not be null");
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.TYPE)
            .text(type.value())
            .orderedLong(position.value())
            .bytes();
    }
}
