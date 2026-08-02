package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.key.SubjectRevisionKey;

/**
 * Subject revision index that maps a subject revision to its global position.
 */
final class SubjectRevisions {

    void remember(
        final Namespace namespace,
        final Subject subject,
        final SubjectRevision revision,
        final Position position,
        final RocksWriteBatch batch
    ) {
        batch.put(
            RocksColumn.SUBJECT_REVISIONS,
            new SubjectRevisionKey(namespace, subject, revision),
            new LongBytes(position.value())
        );
    }
}
