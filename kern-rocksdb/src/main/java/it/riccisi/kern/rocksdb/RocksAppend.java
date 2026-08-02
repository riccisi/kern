package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.StoredEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One prepared append materialized into a RocksDB write batch.
 */
final class RocksAppend {
    private final PreparedAppend append;
    private final PersistedAppendState state;
    private final RocksTables tables;
    private final RocksWriteBatch batch;

    RocksAppend(
        final PreparedAppend append,
        final PersistedAppendState state,
        final RocksTables tables,
        final RocksWriteBatch batch
    ) {
        this.append = Objects.requireNonNull(append, "prepared append must not be null");
        this.state = Objects.requireNonNull(state, "append state must not be null");
        this.tables = Objects.requireNonNull(tables, "RocksDB tables must not be null");
        this.batch = Objects.requireNonNull(batch, "RocksDB batch must not be null");
    }

    AppendResult result() {
        var request = append.request();
        for (Subject subject : request.observedSubjects()) {
            if (!state.knows(subject)) {
                state.remember(subject, tables.subjectHeads().revision(request.namespace(), subject));
            }
        }
        for (ConsistencyKey key : request.observedConsistencyKeys()) {
            if (!state.knows(key)) {
                state.remember(key, tables.consistency().revision(request.namespace(), key));
            }
        }
        request.condition().verify(state, append.diagnosticRequestId());
        Position from = state.nextPosition();
        Map<Subject, SubjectRevision> subjectRevisions = new HashMap<>();
        Map<ConsistencyKey, ConsistencyRevision> consistencyRevisions = new HashMap<>();
        Position position = from;
        for (int index = 0; index < request.events().size(); index++) {
            EventData event = request.events().get(index);
            SubjectRevision revision = state.nextRevision(event.subject());
            subjectRevisions.put(event.subject(), revision);
            StoredEvent stored = new StoredEvent(
                request.namespace(),
                position,
                revision,
                event,
                append.receivedAt()
            );
            tables.records().remember(stored, batch);
            tables.ids().remember(stored, batch);
            tables.subjectRevisions().remember(request.namespace(), event.subject(), revision, position, batch);
            tables.subjectHeads().remember(request.namespace(), event.subject(), revision, batch);
            if (index < request.events().size() - 1) {
                position = state.nextPosition();
            }
        }
        for (ConsistencyKey key : request.touchedConsistencyKeys()) {
            state.touch(key, position);
            consistencyRevisions.put(key, new ConsistencyRevision(position.value()));
            tables.consistency().touch(request.namespace(), key, position, batch);
        }
        return new AppendResult(from, position, subjectRevisions, consistencyRevisions, false);
    }
}
