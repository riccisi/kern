package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.StoredEvent;
import java.util.Objects;
import java.util.Optional;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.Snapshot;

/**
 * Point-in-time RocksDB view over Kern records and indexes.
 */
final class RocksReadSnapshot implements ReadSnapshot {
    private final RocksDB database;
    private final Snapshot snapshot;
    private final ReadOptions options;
    private final RocksTables tables;
    private final SequencePosition watermark;

    RocksReadSnapshot(final RocksDB database, final RocksColumnFamilies families) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.snapshot = database.getSnapshot();
        this.options = new ReadOptions().setSnapshot(snapshot);
        this.tables = new RocksTables(new RocksReader(database, families, options));
        this.watermark = tables.metadata().highWatermark();
    }

    @Override
    public QueryResult read(final ReadRequest request) {
        Objects.requireNonNull(request, "read request must not be null");
        return tables.records().result(request, watermark);
    }

    @Override
    public Optional<StoredEvent> eventById(final Namespace namespace, final EventId id) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(id, "event id must not be null");
        return tables.ids().event(namespace, id);
    }

    @Override
    public SequencePosition highWatermark() {
        return watermark;
    }

    @Override
    public void close() {
        options.close();
        database.releaseSnapshot(snapshot);
    }
}
