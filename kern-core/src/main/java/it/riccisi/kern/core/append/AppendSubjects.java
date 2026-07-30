package it.riccisi.kern.core.append;

import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.append.ExpectedSubjectRevision;
import it.riccisi.kern.api.append.NoSubject;
import it.riccisi.kern.api.value.Subject;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class AppendSubjects {
    private final AppendRequest request;

    AppendSubjects(final AppendRequest request) {
        this.request = Objects.requireNonNull(request, "append request must not be null");
    }

    Set<Subject> asSet() {
        Set<Subject> subjects = new LinkedHashSet<>();
        for (EventData event : request.events()) {
            subjects.add(event.subject());
        }
        if (request.condition() instanceof ExpectedSubjectRevision condition) {
            subjects.add(condition.subject());
        }
        if (request.condition() instanceof NoSubject condition) {
            subjects.add(condition.subject());
        }
        return Set.copyOf(subjects);
    }
}
