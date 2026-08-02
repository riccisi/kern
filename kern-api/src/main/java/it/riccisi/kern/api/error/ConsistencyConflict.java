package it.riccisi.kern.api.error;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import java.util.Objects;

public final class ConsistencyConflict extends EventStoreException {
    private final ConsistencyKey key;
    private final ConsistencyRevision expected;
    private final ConsistencyRevision actual;

    public ConsistencyConflict(
        String diagnosticId,
        ConsistencyKey key,
        ConsistencyRevision expected,
        ConsistencyRevision actual
    ) {
        super(diagnosticId, "consistency key revision does not match expected revision");
        this.key = Objects.requireNonNull(key, "consistency key must not be null");
        this.expected = Objects.requireNonNull(expected, "expected revision must not be null");
        this.actual = Objects.requireNonNull(actual, "actual revision must not be null");
    }

    public ConsistencyKey key() {
        return key;
    }

    public ConsistencyRevision expected() {
        return expected;
    }

    public ConsistencyRevision actual() {
        return actual;
    }
}
