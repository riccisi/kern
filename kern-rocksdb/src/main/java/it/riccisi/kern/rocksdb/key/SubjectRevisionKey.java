package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.Objects;

public record SubjectRevisionKey(
    Namespace namespace,
    Subject subject,
    SubjectRevision revision
) implements BinaryKey {
    public SubjectRevisionKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(revision, "subject revision must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.SUBJECT_REVISION)
            .text(subject.value())
            .orderedLong(revision.value())
            .bytes();
    }
}
