package it.riccisi.kern.rocksdb;

/**
 * Persistent structures that compose the RocksDB representation of Kern.
 */
final class RocksTables {
    private final StoreMetadata metadata;
    private final EventRecords records;
    private final EventIndexes indexes;
    private final EventIds ids;

    RocksTables(final RocksReader reader) {
        this.metadata = new StoreMetadata(reader);
        this.records = new EventRecords(reader);
        this.indexes = new EventIndexes();
        this.ids = new EventIds(reader, records);
    }

    StoreMetadata metadata() {
        return metadata;
    }

    EventRecords records() {
        return records;
    }

    EventIndexes indexes() {
        return indexes;
    }

    EventIds ids() {
        return ids;
    }
}
