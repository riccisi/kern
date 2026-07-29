package it.riccisi.kern.api.error;

public final class StoreOverloadedException extends EventStoreException {
    public StoreOverloadedException(String diagnosticId) {
        super(diagnosticId, "event store append queue is overloaded");
    }
}
