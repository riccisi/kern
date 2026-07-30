package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import java.util.Arrays;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;

public abstract class BinaryKeyEnvelope extends BytesEnvelope implements BinaryKey {

    protected BinaryKeyEnvelope(final Bytes origin) {
        super(origin);
    }

    @Override
    public final boolean equals(final Object other) {
        return this == other
            || other instanceof BinaryKey key
            && Arrays.equals(new UncheckedBytes(this).asBytes(), new UncheckedBytes(key).asBytes());
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(new UncheckedBytes(this).asBytes());
    }
}
