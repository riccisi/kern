package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import java.util.UUID;

public final class UuidBytes extends BytesEnvelope {
    public UuidBytes(UUID uuid) {
        super(
            new JoinedBytes(
                new LongBytes(Objects.requireNonNull(uuid, "uuid must not be null").getMostSignificantBits()),
                new LongBytes(uuid.getLeastSignificantBits())
            )
        );
    }
}
