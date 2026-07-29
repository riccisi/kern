package it.riccisi.kern.core.storage;

import java.util.Arrays;
import java.util.Objects;

public record RequestDigest(byte[] bytes) {
    public RequestDigest {
        bytes = Objects.requireNonNull(bytes, "request digest bytes must not be null").clone();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("request digest bytes must not be empty");
        }
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RequestDigest digest && Arrays.equals(bytes, digest.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
