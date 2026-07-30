package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import org.cactoos.Bytes;

public final class BinaryFieldFromInput implements Bytes {

    private final BinaryInput input;

    public BinaryFieldFromInput(final BinaryInput input) {
        this.input = Objects.requireNonNull(input, "binary input must not be null");
    }

    @Override
    public byte[] asBytes() {
        int length = input.nextInt();
        if (length < 0) {
            throw new MalformedBinaryInputException("field length must not be negative");
        }
        return input.nextBytes(length);
    }
}
