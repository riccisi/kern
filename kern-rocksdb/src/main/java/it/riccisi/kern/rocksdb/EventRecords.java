package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.core.storage.Direction;
import it.riccisi.kern.core.storage.EventPage;
import it.riccisi.kern.core.storage.EventQuery;
import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.key.EventKey;
import it.riccisi.kern.rocksdb.record.EncodedStoredEventRecord;
import it.riccisi.kern.rocksdb.record.StoredEventRecord;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Event records addressed by global position.
 */
final class EventRecords {
    private final RocksReader reader;

    EventRecords(final RocksReader reader) {
        this.reader = Objects.requireNonNull(reader, "RocksDB reader must not be null");
    }

    void remember(final StoredEvent event, final RocksWriteBatch batch) {
        batch.put(RocksColumn.EVENTS, new EventKey(event.namespace(), event.position()), new StoredEventRecord(event));
    }

    Optional<StoredEvent> at(final Namespace namespace, final Position position) {
        return reader.bytes(RocksColumn.EVENTS, new EventKey(namespace, position))
            .map(bytes -> new EncodedStoredEventRecord(bytes).value());
    }

    EventPage page(final EventQuery query, final Position highWatermark) {
        var events = new ArrayList<StoredEvent>();
        Position position = firstPosition(query, highWatermark);
        while (inside(position, highWatermark) && events.size() < query.limit()) {
            at(query.namespace(), position)
                .filter(query::accepts)
                .ifPresent(events::add);
            position = next(position, query.direction());
        }
        return new EventPage(events, Optional.empty(), highWatermark);
    }

    private Position firstPosition(final EventQuery query, final Position highWatermark) {
        Position position;
        if (query.direction() == Direction.FORWARD) {
            position = query.afterPosition().next();
        } else if (query.afterPosition().value() == 0) {
            position = highWatermark;
        } else if (query.afterPosition().value() > highWatermark.value()) {
            position = highWatermark;
        } else {
            position = new Position(query.afterPosition().value() - 1);
        }
        return position;
    }

    private boolean inside(final Position position, final Position highWatermark) {
        return position.value() > 0 && position.value() <= highWatermark.value();
    }

    private Position next(final Position position, final Direction direction) {
        Position next;
        if (direction == Direction.FORWARD) {
            next = position.next();
        } else {
            next = new Position(position.value() - 1);
        }
        return next;
    }
}
