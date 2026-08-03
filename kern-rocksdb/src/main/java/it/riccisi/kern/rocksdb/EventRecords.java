package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.api.event.SequencedEvent;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
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
final class EventRecords implements EventColumnFamily {
    private final RocksReader reader;

    EventRecords(final RocksReader reader) {
        this.reader = Objects.requireNonNull(reader, "RocksDB reader must not be null");
    }

    @Override
    public void remember(final StoredEvent event, final RocksWriteBatch batch) {
        batch.put(RocksColumn.EVENTS, new EventKey(event.namespace(), event.position()), new StoredEventRecord(event));
    }

    Optional<StoredEvent> at(final Namespace namespace, final SequencePosition position) {
        return reader.bytes(RocksColumn.EVENTS, new EventKey(namespace, position))
            .map(bytes -> new EncodedStoredEventRecord(bytes).value());
    }

    QueryResult result(final ReadRequest request, final SequencePosition highWatermark) {
        var events = new ArrayList<SequencedEvent>();
        SequencePosition position = request.fromExclusive().next();
        while (inside(position, highWatermark) && events.size() < request.limit()) {
            at(request.namespace(), position)
                .filter(event -> request.query().matches(event.data()))
                .map(event -> new SequencedEvent(event.position(), event.recordedAt(), event.data()))
                .ifPresent(events::add);
            position = position.next();
        }
        return new QueryResult(events, highWatermark, Optional.empty());
    }

    Optional<StoredEvent> firstMatching(
        final Namespace namespace,
        final it.riccisi.kern.api.query.EventQuery query,
        final SequencePosition fromInclusive,
        final SequencePosition toInclusive
    ) {
        Optional<StoredEvent> found = Optional.empty();
        SequencePosition position = fromInclusive;
        while (found.isEmpty() && inside(position, toInclusive)) {
            found = at(namespace, position)
                .filter(event -> query.matches(event.data()));
            if (found.isEmpty()) {
                position = position.next();
            }
        }
        return found;
    }

    private boolean inside(final SequencePosition position, final SequencePosition highWatermark) {
        return position.value() > 0 && position.value() <= highWatermark.value();
    }
}
