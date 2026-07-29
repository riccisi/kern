package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import java.util.Objects;

public record EventIdKey(Namespace namespace, EventId id) implements BinaryKey {
    public EventIdKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(id, "event id must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.EVENT_ID)
            .uuid(id.value())
            .bytes();
    }
}
