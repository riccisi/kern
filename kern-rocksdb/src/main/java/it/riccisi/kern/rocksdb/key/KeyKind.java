package it.riccisi.kern.rocksdb.key;

import org.cactoos.Bytes;

enum KeyKind implements Bytes {
    SYSTEM((byte) 0x00),
    EVENT((byte) 0x01),
    EVENT_ID((byte) 0x02),
    TYPE((byte) 0x03),
    TAG((byte) 0x04),
    TAG_TYPE((byte) 0x05),
    IDEMPOTENCY((byte) 0x06);

    private final byte marker;

    KeyKind(byte marker) {
        this.marker = marker;
    }

    @Override
    public byte[] asBytes() {
        return new byte[] {marker};
    }
}
