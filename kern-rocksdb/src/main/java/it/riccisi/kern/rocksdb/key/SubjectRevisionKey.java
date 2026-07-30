package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class SubjectRevisionKey extends BinaryKeyEnvelope {

    public SubjectRevisionKey(final Namespace namespace, final Subject subject, final SubjectRevision revision) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.SUBJECT_REVISION,
                new TextBytes(Objects.requireNonNull(subject, "subject must not be null").value()),
                new LongBytes(Objects.requireNonNull(revision, "subject revision must not be null").value())
            )
        );
    }
}
