package it.riccisi.kern.rocksdb;

/**
 * Persistent structures that compose the RocksDB representation of Kern.
 */
final class RocksTables {
    private final StoreMetadata metadata;
    private final SubjectHeads subjectHeads;
    private final SubjectRevisions subjectRevisions;
    private final ConsistencyRevisions consistency;
    private final EventRecords records;
    private final EventIds ids;

    RocksTables(final RocksReader reader) {
        this.metadata = new StoreMetadata(reader);
        this.subjectHeads = new SubjectHeads(reader);
        this.subjectRevisions = new SubjectRevisions();
        this.consistency = new ConsistencyRevisions(reader);
        this.records = new EventRecords(reader);
        this.ids = new EventIds(reader, records);
    }

    StoreMetadata metadata() {
        return metadata;
    }

    SubjectHeads subjectHeads() {
        return subjectHeads;
    }

    SubjectRevisions subjectRevisions() {
        return subjectRevisions;
    }

    ConsistencyRevisions consistency() {
        return consistency;
    }

    EventRecords records() {
        return records;
    }

    EventIds ids() {
        return ids;
    }
}
