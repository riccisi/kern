package it.riccisi.kern.rocksdb.key;

import java.util.Objects;

public record SystemBinaryKey(SystemKey key) implements BinaryKey {
    public SystemBinaryKey {
        Objects.requireNonNull(key, "system key must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .kind(KeyKind.SYSTEM)
            .byteValue(key.code())
            .bytes();
    }
}
