package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.append.Durability;

/**
 * Storage capability used by the core to commit appends and read a consistent
 * point-in-time view without depending on a concrete storage engine.
 */
public interface EventStorage extends AutoCloseable {
    /**
     * Atomically evaluates each query condition, assigns sequence positions and
     * commits event records, indexes, idempotency records, metadata and audit
     * records when applicable.
     */
    CommitOutcome commit(Iterable<PreparedAppend> appends, Durability durability);

    ReadSnapshot snapshot();

    StorageDiagnostics diagnostics();

    void flush(FlushMode mode);

    @Override
    void close();
}
