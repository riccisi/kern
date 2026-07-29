package it.riccisi.kern.rocksdb.key;

enum KeyKind {
    SYSTEM((byte) 0x00),
    EVENT((byte) 0x01),
    SUBJECT_REVISION((byte) 0x02),
    EVENT_ID((byte) 0x03),
    TYPE((byte) 0x04),
    TAG((byte) 0x05),
    SUBJECT_HEAD((byte) 0x06),
    CONSISTENCY((byte) 0x07),
    IDEMPOTENCY((byte) 0x08);

    private final byte marker;

    KeyKind(byte marker) {
        this.marker = marker;
    }

    void writeTo(KeyBuffer buffer) {
        buffer.byteValue(marker);
    }
}
