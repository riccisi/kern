package it.riccisi.kern.api.error;

public final class InvalidAppendException extends EventStoreException {
    public InvalidAppendException(String diagnosticId, String message) {
        super(diagnosticId, message);
    }
}
