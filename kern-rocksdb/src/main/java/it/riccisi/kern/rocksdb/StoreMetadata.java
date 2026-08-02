package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.LongFromBytes;
import it.riccisi.kern.rocksdb.key.SystemKey;
import it.riccisi.kern.rocksdb.key.SystemKeyBinary;
import java.util.OptionalLong;

/**
 * System metadata persisted with the event store.
 */
final class StoreMetadata {
    private static final long FORMAT_VERSION = 2L;

    private final RocksReader reader;

    StoreMetadata(final RocksReader reader) {
        this.reader = reader;
    }

    SequencePosition highWatermark() {
        return reader.bytes(RocksColumn.METADATA, new SystemKeyBinary(SystemKey.NEXT_POSITION))
            .map(bytes -> new SequencePosition(new LongFromBytes(bytes).value()))
            .orElse(new SequencePosition(0));
    }

    OptionalLong formatVersion() {
        return reader.bytes(RocksColumn.METADATA, new SystemKeyBinary(SystemKey.FORMAT_VERSION))
            .map(bytes -> OptionalLong.of(new LongFromBytes(bytes).value()))
            .orElse(OptionalLong.empty());
    }

    void verifyFormat() {
        OptionalLong version = formatVersion();
        if (version.isPresent() && version.getAsLong() != FORMAT_VERSION) {
            throw new IllegalStateException("RocksDB storage format is not compatible with Kern v0.2");
        }
    }

    void remember(final SequencePosition position, final RocksWriteBatch batch) {
        batch.put(RocksColumn.METADATA, new SystemKeyBinary(SystemKey.NEXT_POSITION), new LongBytes(position.value()));
    }

    void rememberFormat(final RocksWriteBatch batch) {
        batch.put(RocksColumn.METADATA, new SystemKeyBinary(SystemKey.FORMAT_VERSION), new LongBytes(FORMAT_VERSION));
    }
}
