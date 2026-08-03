package it.riccisi.kern.rocksdb;

import it.riccisi.kern.core.storage.StoredEvent;

/**
 * Persistent RocksDB structure that records a committed event inside a batch.
 */
interface EventColumnFamily {

    void remember(StoredEvent event, RocksWriteBatch batch);
}
