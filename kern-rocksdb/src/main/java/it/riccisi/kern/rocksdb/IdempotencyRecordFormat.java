package it.riccisi.kern.rocksdb;

import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.IntBytes;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;

enum IdempotencyRecordFormat implements Bytes {
    V1(0x4B494450, 1);

    private final int magic;
    private final int version;

    IdempotencyRecordFormat(final int magic, final int version) {
        this.magic = magic;
        this.version = version;
    }

    @Override
    public byte[] asBytes() {
        return new UncheckedBytes(
            new JoinedBytes(
                new IntBytes(magic),
                new IntBytes(version)
            )
        ).asBytes();
    }

    void readFrom(final BinaryInput input) {
        if (input.nextInt() != magic) {
            throw new IllegalStateException("idempotency record magic mismatch");
        }
        int actual = input.nextInt();
        if (actual != version) {
            throw new IllegalStateException("unsupported idempotency record version " + actual);
        }
    }
}
