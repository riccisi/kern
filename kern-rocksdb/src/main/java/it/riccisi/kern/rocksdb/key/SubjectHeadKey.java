package it.riccisi.kern.rocksdb.key;

import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import java.util.Objects;

public record SubjectHeadKey(Namespace namespace, Subject subject) implements BinaryKey {
    public SubjectHeadKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
    }

    @Override
    public byte[] bytes() {
        return new KeyBuffer()
            .text(namespace.value())
            .kind(KeyKind.SUBJECT_HEAD)
            .text(subject.value())
            .bytes();
    }
}
