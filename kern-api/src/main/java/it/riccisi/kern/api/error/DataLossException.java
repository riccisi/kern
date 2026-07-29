package it.riccisi.kern.api.error;

public final class DataLossException extends EventStoreException {
    public DataLossException(String diagnosticId) {
        super(diagnosticId, "event store data loss was detected");
    }
}
