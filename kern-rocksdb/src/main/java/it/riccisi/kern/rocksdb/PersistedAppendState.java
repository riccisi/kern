package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendConditionState;
import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.HashMap;
import java.util.Map;

final class PersistedAppendState implements AppendConditionState {
    private Position watermark;
    private final Map<Subject, SubjectRevision> revisions;
    private final Map<ConsistencyKey, ConsistencyRevision> consistency;

    PersistedAppendState(
        final Position watermark,
        final Map<Subject, SubjectRevision> revisions,
        final Map<ConsistencyKey, ConsistencyRevision> consistency
    ) {
        this.watermark = watermark;
        this.revisions = new HashMap<>(revisions);
        this.consistency = new HashMap<>(consistency);
    }

    Position nextPosition() {
        this.watermark = watermark.next();
        return watermark;
    }

    @Override
    public SubjectRevision subjectRevision(final Subject subject) {
        return revisions.getOrDefault(subject, new SubjectRevision(0));
    }

    SubjectRevision nextRevision(final Subject subject) {
        SubjectRevision next = subjectRevision(subject).next();
        revisions.put(subject, next);
        return next;
    }

    boolean knows(final Subject subject) {
        return revisions.containsKey(subject);
    }

    boolean knows(final ConsistencyKey key) {
        return consistency.containsKey(key);
    }

    void remember(final Subject subject, final SubjectRevision revision) {
        revisions.put(subject, revision);
    }

    void remember(final ConsistencyKey key, final ConsistencyRevision revision) {
        consistency.put(key, revision);
    }

    void touch(final ConsistencyKey key, final Position position) {
        consistency.put(key, new ConsistencyRevision(position.value()));
    }

    Position highWatermark() {
        return watermark;
    }

    Map<Subject, SubjectRevision> revisions() {
        return Map.copyOf(revisions);
    }

    @Override
    public ConsistencyRevision consistencyRevision(final ConsistencyKey key) {
        return consistency.getOrDefault(key, new ConsistencyRevision(0));
    }
}
