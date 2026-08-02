package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.LongFromBytes;
import it.riccisi.kern.rocksdb.key.SubjectHeadKey;

/**
 * Subject head revisions persisted by namespace and subject.
 */
final class SubjectHeads {
    private final RocksReader reader;

    SubjectHeads(final RocksReader reader) {
        this.reader = reader;
    }

    SubjectRevision revision(final Namespace namespace, final Subject subject) {
        return reader.bytes(RocksColumn.SUBJECT_HEADS, new SubjectHeadKey(namespace, subject))
            .map(bytes -> new SubjectRevision(new LongFromBytes(bytes).value()))
            .orElse(new SubjectRevision(0));
    }

    void remember(
        final Namespace namespace,
        final Subject subject,
        final SubjectRevision revision,
        final RocksWriteBatch batch
    ) {
        batch.put(RocksColumn.SUBJECT_HEADS, new SubjectHeadKey(namespace, subject), new LongBytes(revision.value()));
    }
}
