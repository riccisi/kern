package it.riccisi.kern.api.error;

import java.util.Objects;

public abstract sealed class EventStoreException extends RuntimeException
    permits DataLossException, IdempotencyConflict, InvalidAppendException, QueryConflict,
    StorageUnavailableException, StoreOverloadedException {
    private final String diagnosticId;

    protected EventStoreException(String diagnosticId, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.diagnosticId = Objects.requireNonNull(diagnosticId, "diagnostic id must not be null");
        if (diagnosticId.isBlank()) {
            throw new IllegalArgumentException("diagnostic id must not be blank");
        }
    }

    public String diagnosticId() {
        return diagnosticId;
    }
}
