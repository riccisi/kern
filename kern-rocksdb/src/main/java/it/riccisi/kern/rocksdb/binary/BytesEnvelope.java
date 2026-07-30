package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import org.cactoos.Bytes;

public abstract class BytesEnvelope implements Bytes {

    private final Bytes origin;

    protected BytesEnvelope(Bytes origin) {
        this.origin = Objects.requireNonNull(origin, "bytes origin must not be null");
    }

    @Override
    public final byte[] asBytes() throws Exception {
        return origin.asBytes();
    }
}
