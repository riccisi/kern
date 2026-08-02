package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import org.cactoos.Scalar;

public final class LongFromBytes implements Scalar<Long> {
    private final byte[] bytes;

    public LongFromBytes(final byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "long bytes must not be null").clone();
    }

    @Override
    public Long value() {
        if (bytes.length != Long.BYTES) {
            throw new IllegalArgumentException("long bytes must contain exactly eight bytes");
        }
        long value = 0L;
        for (byte part : bytes) {
            value = (value << Byte.SIZE) | (part & 0xFFL);
        }
        return value;
    }
}
