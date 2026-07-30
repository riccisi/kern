package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import org.cactoos.bytes.BytesOf;

public final class BinaryFieldBytes extends BytesEnvelope {

    public BinaryFieldBytes(final byte[] bytes) {
        super(
            new JoinedBytes(
                new IntBytes(Objects.requireNonNull(bytes, "binary field must not be null").length),
                new BytesOf(bytes.clone())
            )
        );
    }
}
