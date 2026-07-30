package it.riccisi.kern.rocksdb.key;

import java.util.Arrays;
import java.util.Objects;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;

public interface BinaryKey extends Bytes, Comparable<BinaryKey> {

    @Override
    default int compareTo(final BinaryKey other) {
        return Arrays.compareUnsigned(
            new UncheckedBytes(this).asBytes(),
            new UncheckedBytes(Objects.requireNonNull(other, "other key must not be null")).asBytes()
        );
    }
}
