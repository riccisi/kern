package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.IntBytes;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;

enum EventRecordFormat implements Bytes {
    V1(0x4B45524E, 1);

    private final int magic;
    private final int version;

    EventRecordFormat(int magic, int version) {
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
            throw new CorruptEventRecordException("event record magic mismatch");
        }
        int actual = input.nextInt();
        if (actual != version) {
            throw new UnsupportedEventRecordException(actual);
        }
    }
}
