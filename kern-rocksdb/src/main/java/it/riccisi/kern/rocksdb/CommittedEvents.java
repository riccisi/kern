package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.error.QueryConflict;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.StoredEvent;
import java.util.Objects;

/**
 * Authoritative committed event log plus every derived event projection.
 */
final class CommittedEvents {
    private final EventRecords records;
    private final EventColumnFamily columns;

    CommittedEvents(final EventRecords records, final EventColumnFamily columns) {
        this.records = Objects.requireNonNull(records, "event records must not be null");
        this.columns = Objects.requireNonNull(columns, "event column families must not be null");
    }

    AppendResult append(
        final PreparedAppend append,
        final SequencePosition highWatermark,
        final RocksWriteBatch batch
    ) {
        Objects.requireNonNull(batch, "RocksDB batch must not be null");
        PreparedAppend prepared = Objects.requireNonNull(append, "prepared append must not be null");
        SequencePosition watermark = Objects.requireNonNull(highWatermark, "high watermark must not be null");
        this.rejectConflicting(prepared, watermark);
        return this.remember(prepared, watermark, batch);
    }

    private void rejectConflicting(final PreparedAppend append, final SequencePosition highWatermark) {
        AppendRequest request = append.request();
        records.firstMatching(
            request.namespace(),
            request.condition().failIfEventsMatch(),
            request.condition().after().next(),
            highWatermark
        ).ifPresent(conflict -> {
            int matched = request.condition().failIfEventsMatch()
                .matchingItem(conflict.data())
                .orElseThrow();
            throw new QueryConflict(
                append.diagnosticRequestId(),
                request.condition().after(),
                conflict.position(),
                conflict.data().id(),
                conflict.data().type(),
                conflict.data().tags(),
                matched
            );
        });
    }

    private AppendResult remember(
        final PreparedAppend append,
        final SequencePosition highWatermark,
        final RocksWriteBatch batch
    ) {
        AppendRequest request = append.request();
        SequencePosition from = highWatermark.next();
        SequencePosition position = from;
        for (int index = 0; index < request.events().size(); index++) {
            EventData event = request.events().get(index);
            columns.remember(
                new StoredEvent(request.namespace(), position, event, append.receivedAt()),
                batch
            );
            if (index < request.events().size() - 1) {
                position = position.next();
            }
        }
        return new AppendResult(from, position, false);
    }
}
