package it.riccisi.kern.rocksdb.record;

public final class UnsupportedEventRecordException extends RuntimeException {
    public UnsupportedEventRecordException(int version) {
        super("event record format version " + version + " is not supported");
    }
}
