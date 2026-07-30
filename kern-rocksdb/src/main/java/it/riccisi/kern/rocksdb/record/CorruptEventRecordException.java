package it.riccisi.kern.rocksdb.record;

public final class CorruptEventRecordException extends RuntimeException {

    public CorruptEventRecordException(final String message) {
        super(message);
    }

    public CorruptEventRecordException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
