package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import java.util.zip.CRC32C;

public final class ChecksummedBinaryInput implements BinaryInput {

    private final BinaryInput origin;
    private final CRC32C checksum;

    public ChecksummedBinaryInput(final BinaryInput origin) {
        this.origin = Objects.requireNonNull(origin, "input must not be null");
        this.checksum = new CRC32C();
    }

    @Override
    public int nextInt() {
        int value = origin.nextInt();
        for (int shift = Integer.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            checksum.update(value >>> shift);
        }
        return value;
    }

    @Override
    public long nextLong() {
        long value = origin.nextLong();
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            checksum.update((int) (value >>> shift));
        }
        return value;
    }

    @Override
    public byte[] nextBytes(final int length) {
        byte[] value = origin.nextBytes(length);
        checksum.update(value, 0, value.length);
        return value;
    }

    @Override
    public void exhausted() {
        int expected = origin.nextInt();
        origin.exhausted();
        if (expected != (int) checksum.getValue()) {
            throw new MalformedBinaryInputException("checksum mismatch");
        }
    }
}
