package it.riccisi.kern.core.append;

import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.Revisions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AssignedSubjectRevisions {
    private final List<EventData> events;
    private final Revisions revisions;

    AssignedSubjectRevisions(final List<EventData> events, final Revisions revisions) {
        this.events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        this.revisions = Objects.requireNonNull(revisions, "revisions must not be null");
    }

    Map<Subject, SubjectRevision> asMap() {
        Map<Subject, SubjectRevision> current = new HashMap<>(revisions.subjects());
        Map<Subject, SubjectRevision> assigned = new HashMap<>();
        for (EventData event : events) {
            SubjectRevision revision = current.getOrDefault(event.subject(), new SubjectRevision(0)).next();
            current.put(event.subject(), revision);
            assigned.put(event.subject(), revision);
        }
        return Map.copyOf(assigned);
    }
}
