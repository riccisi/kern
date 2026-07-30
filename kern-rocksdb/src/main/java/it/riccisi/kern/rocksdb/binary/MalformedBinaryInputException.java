package it.riccisi.kern.rocksdb.binary;

public final class MalformedBinaryInputException extends RuntimeException {

    public MalformedBinaryInputException(final String message) {
        super(message);
    }
}
