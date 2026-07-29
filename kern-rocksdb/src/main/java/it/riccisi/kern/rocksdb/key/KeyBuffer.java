package it.riccisi.kern.rocksdb.key;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

final class KeyBuffer {
    private final ByteArrayOutputStream bytes;

    KeyBuffer() {
        this.bytes = new ByteArrayOutputStream(64);
    }

    KeyBuffer kind(KeyKind kind) {
        Objects.requireNonNull(kind, "key kind must not be null").writeTo(this);
        return this;
    }

    KeyBuffer byteValue(byte value) {
        bytes.write(value);
        return this;
    }

    KeyBuffer text(String value) {
        byte[] encoded = Objects.requireNonNull(value, "text component must not be null")
            .getBytes(StandardCharsets.UTF_8);
        intValue(encoded.length);
        bytes.writeBytes(encoded);
        return this;
    }

    KeyBuffer orderedLong(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("numeric key component must not be negative");
        }
        rawLong(value);
        return this;
    }

    KeyBuffer uuid(UUID value) {
        Objects.requireNonNull(value, "uuid component must not be null");
        rawLong(value.getMostSignificantBits());
        rawLong(value.getLeastSignificantBits());
        return this;
    }

    byte[] bytes() {
        return bytes.toByteArray();
    }

    private void intValue(int value) {
        bytes.write((byte) (value >>> 24));
        bytes.write((byte) (value >>> 16));
        bytes.write((byte) (value >>> 8));
        bytes.write((byte) value);
    }

    private void rawLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            bytes.write((byte) (value >>> shift));
        }
    }
}
