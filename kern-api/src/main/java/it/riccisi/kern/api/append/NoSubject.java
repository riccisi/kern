package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.Subject;
import java.util.Objects;

public record NoSubject(Subject subject) implements AppendCondition {
    public NoSubject {
        Objects.requireNonNull(subject, "subject must not be null");
    }
}
