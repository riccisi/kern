package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.LongFromBytes;
import it.riccisi.kern.rocksdb.key.EventIdKey;
import java.util.Optional;

/**
 * Event id index that maps client event ids to global positions.
 */
final class EventIds {
    private final RocksReader reader;
    private final EventRecords records;

    EventIds(final RocksReader reader, final EventRecords records) {
        this.reader = reader;
        this.records = records;
    }

    void remember(final StoredEvent event, final RocksWriteBatch batch) {
        batch.put(RocksColumn.EVENT_IDS, new EventIdKey(event.namespace(), event.data().id()), new LongBytes(event.position().value()));
    }

    Optional<StoredEvent> event(final Namespace namespace, final EventId id) {
        return reader.bytes(RocksColumn.EVENT_IDS, new EventIdKey(namespace, id))
            .map(bytes -> new Position(new LongFromBytes(bytes).value()))
            .flatMap(position -> records.at(namespace, position));
    }
}
