package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.error.IdempotencyConflict;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.rocksdb.key.IdempotencyRecordKey;
import java.util.Objects;
import java.util.Optional;

/**
 * RocksDB table of accepted append requests keyed by idempotency key.
 */
final class IdempotencyRecords {
    private final RocksReader reader;

    IdempotencyRecords(final RocksReader reader) {
        this.reader = Objects.requireNonNull(reader, "RocksDB reader must not be null");
    }

    Optional<AppendResult> replay(final PreparedAppend append) {
        Objects.requireNonNull(append, "prepared append must not be null");
        return reader.bytes(
            RocksColumn.IDEMPOTENCY,
            new IdempotencyRecordKey(append.request().namespace(), append.request().idempotencyKey())
        ).map(bytes -> this.replay(append, bytes));
    }

    void remember(final PreparedAppend append, final AppendResult result, final RocksWriteBatch batch) {
        PreparedAppend prepared = Objects.requireNonNull(append, "prepared append must not be null");
        AppendResult accepted = Objects.requireNonNull(result, "append result must not be null");
        Objects.requireNonNull(batch, "RocksDB batch must not be null").put(
            RocksColumn.IDEMPOTENCY,
            new IdempotencyRecordKey(prepared.request().namespace(), prepared.request().idempotencyKey()),
            new IdempotencyRecord(prepared.digest(), accepted)
        );
    }

    private AppendResult replay(final PreparedAppend append, final byte[] bytes) {
        EncodedIdempotencyRecord.RecoveredIdempotency recovered = new EncodedIdempotencyRecord(bytes).value();
        if (!recovered.digest().equals(append.digest())) {
            throw new IdempotencyConflict(append.diagnosticRequestId(), append.request().idempotencyKey());
        }
        return recovered.result();
    }
}
