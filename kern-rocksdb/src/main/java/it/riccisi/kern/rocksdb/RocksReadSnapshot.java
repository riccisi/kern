package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.EventPage;
import it.riccisi.kern.core.storage.EventQuery;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.Revisions;
import it.riccisi.kern.core.storage.RevisionQuery;
import it.riccisi.kern.core.storage.StoredEvent;
import java.util.HashMap;
import java.util.Map;
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
    private final Position watermark;

    RocksReadSnapshot(final RocksDB database, final RocksColumnFamilies families) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.snapshot = database.getSnapshot();
        this.options = new ReadOptions().setSnapshot(snapshot);
        this.tables = new RocksTables(new RocksReader(database, families, options));
        this.watermark = tables.metadata().highWatermark();
    }

    @Override
    public EventPage read(final EventQuery query) {
        Objects.requireNonNull(query, "event query must not be null");
        return tables.records().page(query, watermark);
    }

    @Override
    public Revisions revisions(final RevisionQuery query) {
        Objects.requireNonNull(query, "revision query must not be null");
        Map<Subject, SubjectRevision> subjects = new HashMap<>();
        for (Subject subject : query.subjects()) {
            subjects.put(subject, tables.subjectHeads().revision(query.namespace(), subject));
        }
        Map<ConsistencyKey, ConsistencyRevision> consistency = new HashMap<>();
        for (ConsistencyKey key : query.consistencyKeys()) {
            consistency.put(key, tables.consistency().revision(query.namespace(), key));
        }
        return new Revisions(subjects, consistency, watermark);
    }

    @Override
    public Optional<StoredEvent> eventById(final Namespace namespace, final EventId id) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(id, "event id must not be null");
        return tables.ids().event(namespace, id);
    }

    @Override
    public Position highWatermark() {
        return watermark;
    }

    @Override
    public void close() {
        options.close();
        database.releaseSnapshot(snapshot);
    }
}
