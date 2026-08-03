package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.core.storage.RequestDigest;
import it.riccisi.kern.rocksdb.binary.BinaryFieldBytes;
import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.ChecksummedBytes;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import java.util.Objects;

/**
 * Durable idempotency promise for one accepted append request.
 */
final class IdempotencyRecord extends BytesEnvelope {

    IdempotencyRecord(final RequestDigest digest, final AppendResult result) {
        super(
            new ChecksummedBytes(
                new JoinedBytes(
                    IdempotencyRecordFormat.V1,
                    new BinaryFieldBytes(Objects.requireNonNull(digest, "request digest must not be null").bytes()),
                    new LongBytes(Objects.requireNonNull(result, "append result must not be null").fromPosition().value()),
                    new LongBytes(result.toPosition().value())
                )
            )
        );
    }
}
