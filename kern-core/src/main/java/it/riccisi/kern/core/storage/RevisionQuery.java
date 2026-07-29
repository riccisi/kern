package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Subject;
import java.util.Objects;
import java.util.Set;

public record RevisionQuery(
    Namespace namespace,
    Set<Subject> subjects,
    Set<ConsistencyKey> consistencyKeys
) {
    public RevisionQuery {
        Objects.requireNonNull(namespace, "namespace must not be null");
        subjects = Set.copyOf(Objects.requireNonNull(subjects, "subjects must not be null"));
        consistencyKeys = Set.copyOf(Objects.requireNonNull(consistencyKeys, "consistency keys must not be null"));
    }
}
