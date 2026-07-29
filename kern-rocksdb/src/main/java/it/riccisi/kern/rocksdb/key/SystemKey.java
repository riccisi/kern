package it.riccisi.kern.rocksdb.key;

public enum SystemKey {
    FORMAT_VERSION((byte) 0x01),
    NEXT_POSITION((byte) 0x02),
    CLEAN_SHUTDOWN((byte) 0x03);

    private final byte code;

    SystemKey(byte code) {
        this.code = code;
    }

    byte code() {
        return code;
    }
}
