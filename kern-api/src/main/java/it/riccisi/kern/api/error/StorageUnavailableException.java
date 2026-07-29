package it.riccisi.kern.api.error;

public final class StorageUnavailableException extends EventStoreException {
    public StorageUnavailableException(String diagnosticId) {
        super(diagnosticId, "event store storage is unavailable");
    }
}
