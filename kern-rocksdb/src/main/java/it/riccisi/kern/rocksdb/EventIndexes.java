package it.riccisi.kern.rocksdb;

import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.key.EventTagKey;
import it.riccisi.kern.rocksdb.key.EventTagTypeKey;
import it.riccisi.kern.rocksdb.key.EventTypeKey;
import org.cactoos.bytes.BytesOf;

/**
 * Derived posting lists for query planning over tags and event types.
 */
final class EventIndexes implements EventColumnFamily {
    private static final BytesOf EMPTY = new BytesOf(new byte[0]);

    @Override
    public void remember(final StoredEvent event, final RocksWriteBatch batch) {
        batch.put(RocksColumn.TYPE_INDEX, new EventTypeKey(
            event.namespace(),
            event.data().type(),
            event.position()
        ), EMPTY);
        for (var tag : event.data().tags()) {
            batch.put(RocksColumn.TAG_INDEX, new EventTagKey(event.namespace(), tag, event.position()), EMPTY);
            batch.put(RocksColumn.TAG_TYPE_INDEX, new EventTagTypeKey(
                event.namespace(),
                tag,
                event.data().type(),
                event.position()
            ), EMPTY);
        }
    }
}
