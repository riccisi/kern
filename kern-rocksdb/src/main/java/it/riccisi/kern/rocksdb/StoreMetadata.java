package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.LongFromBytes;
import it.riccisi.kern.rocksdb.key.SystemKey;
import it.riccisi.kern.rocksdb.key.SystemKeyBinary;

/**
 * System metadata persisted with the event store.
 */
final class StoreMetadata {
    private final RocksReader reader;

    StoreMetadata(final RocksReader reader) {
        this.reader = reader;
    }

    Position highWatermark() {
        return reader.bytes(RocksColumn.SYSTEM, new SystemKeyBinary(SystemKey.NEXT_POSITION))
            .map(bytes -> new Position(new LongFromBytes(bytes).value()))
            .orElse(new Position(0));
    }

    void remember(final Position position, final RocksWriteBatch batch) {
        batch.put(RocksColumn.SYSTEM, new SystemKeyBinary(SystemKey.NEXT_POSITION), new LongBytes(position.value()));
    }
}
