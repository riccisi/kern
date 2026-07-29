package it.riccisi.kern.rocksdb.key;

import java.util.Arrays;
import java.util.Objects;

public interface BinaryKey extends Comparable<BinaryKey> {
    byte[] bytes();

    default boolean startsWith(BinaryKey prefix) {
        Objects.requireNonNull(prefix, "key prefix must not be null");
        byte[] prefixBytes = prefix.bytes();
        byte[] keyBytes = bytes();
        if (prefixBytes.length > keyBytes.length) {
            return false;
        }
        for (int index = 0; index < prefixBytes.length; index++) {
            if (keyBytes[index] != prefixBytes[index]) {
                return false;
            }
        }
        return true;
    }

    @Override
    default int compareTo(BinaryKey other) {
        Objects.requireNonNull(other, "other key must not be null");
        return Arrays.compareUnsigned(bytes(), other.bytes());
    }
}
