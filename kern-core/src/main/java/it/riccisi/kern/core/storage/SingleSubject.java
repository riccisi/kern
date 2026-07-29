package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Subject;
import java.util.Objects;

public record SingleSubject(Subject subject) implements SubjectFilter {
    public SingleSubject {
        Objects.requireNonNull(subject, "subject must not be null");
    }
}
