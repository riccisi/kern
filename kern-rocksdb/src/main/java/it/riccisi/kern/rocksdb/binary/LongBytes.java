package it.riccisi.kern.rocksdb.binary;

import org.cactoos.Bytes;

public final class LongBytes implements Bytes {
    private final long value;

    public LongBytes(long value) {
        this.value = value;
    }

    @Override
    public byte[] asBytes() {
        byte[] bytes = new byte[Long.BYTES];
        for (int index = 0; index < Long.BYTES; index++) {
            bytes[index] = (byte) (value >>> ((Long.BYTES - 1 - index) * Byte.SIZE));
        }
        return bytes;
    }
}
