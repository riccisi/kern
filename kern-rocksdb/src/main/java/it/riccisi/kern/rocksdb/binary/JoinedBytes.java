package it.riccisi.kern.rocksdb.binary;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.cactoos.Bytes;
import org.cactoos.iterable.IterableOf;

public final class JoinedBytes implements Bytes {
    private final Iterable<Bytes> parts;

    public JoinedBytes(Bytes... parts) {
        this(new IterableOf<>(parts));
    }

    public JoinedBytes(Iterable<Bytes> parts) {
        this.parts = Objects.requireNonNull(parts, "byte parts must not be null");
    }

    @Override
    public byte[] asBytes() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (Bytes part : parts) {
            stream.write(Objects.requireNonNull(part, "byte part must not be null").asBytes());
        }
        return stream.toByteArray();
    }
}
