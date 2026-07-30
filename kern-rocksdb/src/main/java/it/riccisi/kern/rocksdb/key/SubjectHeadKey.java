package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import java.util.Objects;

public final class SubjectHeadKey extends BinaryKeyEnvelope {

    public SubjectHeadKey(final Namespace namespace, final Subject subject) {
        super(
            new JoinedBytes(
                new NamespaceKey(namespace),
                KeyKind.SUBJECT_HEAD,
                new TextBytes(Objects.requireNonNull(subject, "subject must not be null").value())
            )
        );
    }
}
