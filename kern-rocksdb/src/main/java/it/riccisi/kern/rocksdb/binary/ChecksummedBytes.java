package it.riccisi.kern.rocksdb.binary;

import java.util.Objects;
import java.util.zip.CRC32C;
import org.cactoos.Bytes;
import org.cactoos.bytes.BytesOf;
import org.cactoos.bytes.UncheckedBytes;

public final class ChecksummedBytes extends BytesEnvelope {

    public ChecksummedBytes(final Bytes content) {
        super(encoded(content));
    }

    private static Bytes encoded(final Bytes content) {
        byte[] contentBytes = new UncheckedBytes(
            Objects.requireNonNull(content, "content must not be null")
        ).asBytes();
        return new JoinedBytes(
            new BytesOf(contentBytes),
            new IntBytes(checksum(contentBytes))
        );
    }

    private static int checksum(final byte[] bytes) {
        CRC32C checksum = new CRC32C();
        checksum.update(bytes, 0, bytes.length);
        return (int) checksum.getValue();
    }
}
