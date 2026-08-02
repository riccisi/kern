package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Subject;
import java.util.Objects;

/**
 * Subject filter that accepts only one exact subject.
 */
public record SingleSubject(Subject subject) implements SubjectFilter {
    public SingleSubject {
        Objects.requireNonNull(subject, "subject must not be null");
    }

    @Override
    public boolean accepts(final Subject candidate) {
        return subject.equals(Objects.requireNonNull(candidate, "subject must not be null"));
    }
}
