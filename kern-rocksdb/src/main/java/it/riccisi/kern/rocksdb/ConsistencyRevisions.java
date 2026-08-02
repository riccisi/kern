package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.LongFromBytes;
import it.riccisi.kern.rocksdb.key.ConsistencyRevisionKey;

/**
 * Dynamic consistency revisions used by append conditions.
 */
final class ConsistencyRevisions {
    private final RocksReader reader;

    ConsistencyRevisions(final RocksReader reader) {
        this.reader = reader;
    }

    ConsistencyRevision revision(final Namespace namespace, final ConsistencyKey key) {
        return reader.bytes(RocksColumn.CONSISTENCY, new ConsistencyRevisionKey(namespace, key))
            .map(bytes -> new ConsistencyRevision(new LongFromBytes(bytes).value()))
            .orElse(new ConsistencyRevision(0));
    }

    void touch(
        final Namespace namespace,
        final ConsistencyKey key,
        final Position position,
        final RocksWriteBatch batch
    ) {
        batch.put(RocksColumn.CONSISTENCY, new ConsistencyRevisionKey(namespace, key), new LongBytes(position.value()));
    }
}
