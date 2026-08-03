package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.core.storage.RequestDigest;
import it.riccisi.kern.rocksdb.binary.BinaryFieldFromInput;
import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.ByteArrayInput;
import it.riccisi.kern.rocksdb.binary.ChecksummedBinaryInput;
import java.util.Objects;
import org.cactoos.Scalar;

/**
 * Idempotency record materialized from RocksDB bytes.
 */
final class EncodedIdempotencyRecord implements Scalar<EncodedIdempotencyRecord.RecoveredIdempotency> {
    private final BinaryInput input;

    EncodedIdempotencyRecord(final byte[] bytes) {
        this.input = new ChecksummedBinaryInput(new ByteArrayInput(bytes));
    }

    @Override
    public RecoveredIdempotency value() {
        IdempotencyRecordFormat.V1.readFrom(input);
        RequestDigest digest = new RequestDigest(new BinaryFieldFromInput(input).asBytes());
        AppendResult result = new AppendResult(
            new SequencePosition(input.nextLong()),
            new SequencePosition(input.nextLong()),
            true
        );
        input.exhausted();
        return new RecoveredIdempotency(digest, result);
    }

    record RecoveredIdempotency(RequestDigest digest, AppendResult result) {
        RecoveredIdempotency {
            Objects.requireNonNull(digest, "request digest must not be null");
            Objects.requireNonNull(result, "append result must not be null");
        }
    }
}
