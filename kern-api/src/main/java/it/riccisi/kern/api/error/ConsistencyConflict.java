package it.riccisi.kern.api.error;

import it.riccisi.kern.api.value.ConsistencyKey;
import java.util.Objects;

public final class ConsistencyConflict extends EventStoreException {
    private final ConsistencyKey key;
    private final long expected;
    private final long actual;

    public ConsistencyConflict(String diagnosticId, ConsistencyKey key, long expected, long actual) {
        super(diagnosticId, "consistency key revision does not match expected revision");
        this.key = Objects.requireNonNull(key, "consistency key must not be null");
        if (expected < 0) {
            throw new IllegalArgumentException("expected revision must not be negative");
        }
        if (actual < 0) {
            throw new IllegalArgumentException("actual revision must not be negative");
        }
        this.expected = expected;
        this.actual = actual;
    }

    public ConsistencyKey key() {
        return key;
    }

    public long expected() {
        return expected;
    }

    public long actual() {
        return actual;
    }
}
