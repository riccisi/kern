package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Subject;
import java.util.Objects;

/**
 * Subject filter that accepts every subject.
 */
public record AllSubjects() implements SubjectFilter {

    @Override
    public boolean accepts(final Subject subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        return true;
    }
}
