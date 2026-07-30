package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import java.util.Objects;
import org.cactoos.bytes.BytesOf;

public final class SystemKeyBinary extends BinaryKeyEnvelope {

    public SystemKeyBinary(final SystemKey key) {
        super(
            new JoinedBytes(
                KeyKind.SYSTEM,
                new BytesOf(Objects.requireNonNull(key, "system key must not be null").code())
            )
        );
    }
}
