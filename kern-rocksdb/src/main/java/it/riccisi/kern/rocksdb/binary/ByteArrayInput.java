package it.riccisi.kern.rocksdb.binary;

import java.util.Arrays;
import java.util.Objects;

public final class ByteArrayInput implements BinaryInput {

    private final byte[] bytes;
    private int index;

    public ByteArrayInput(final byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "binary input bytes must not be null").clone();
        this.index = 0;
    }

    @Override
    public int nextInt() {
        require(Integer.BYTES);
        int value = ((bytes[index] & 0xFF) << 24)
            | ((bytes[index + 1] & 0xFF) << 16)
            | ((bytes[index + 2] & 0xFF) << 8)
            | (bytes[index + 3] & 0xFF);
        index += Integer.BYTES;
        return value;
    }

    @Override
    public long nextLong() {
        require(Long.BYTES);
        long value = 0L;
        for (int offset = 0; offset < Long.BYTES; offset++) {
            value = (value << 8) | (bytes[index + offset] & 0xFFL);
        }
        index += Long.BYTES;
        return value;
    }

    @Override
    public byte[] nextBytes(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("binary input length must not be negative");
        }
        require(length);
        byte[] value = Arrays.copyOfRange(bytes, index, index + length);
        index += length;
        return value;
    }

    @Override
    public void exhausted() {
        if (index != bytes.length) {
            throw new MalformedBinaryInputException("contains trailing bytes");
        }
    }

    private void require(final int length) {
        if (bytes.length - index < length) {
            throw new MalformedBinaryInputException("is truncated");
        }
    }
}
