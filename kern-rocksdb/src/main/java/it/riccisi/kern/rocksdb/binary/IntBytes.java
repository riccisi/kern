package it.riccisi.kern.rocksdb.binary;

import org.cactoos.Bytes;

public final class IntBytes implements Bytes {
    private final int value;

    public IntBytes(int value) {
        this.value = value;
    }

    @Override
    public byte[] asBytes() {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }
}
