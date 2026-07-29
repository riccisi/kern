package it.riccisi.kern.api.error;

import it.riccisi.kern.api.value.IdempotencyKey;
import java.util.Objects;

public final class IdempotencyConflict extends EventStoreException {
    private final IdempotencyKey key;

    public IdempotencyConflict(String diagnosticId, IdempotencyKey key) {
        super(diagnosticId, "idempotency key was already used by a different append request");
        this.key = Objects.requireNonNull(key, "idempotency key must not be null");
    }

    public IdempotencyKey key() {
        return key;
    }
}
