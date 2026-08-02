package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.ConsistencyRevision;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;

/**
 * Revisions observed while deciding whether an append may be committed.
 */
public interface AppendConditionState {
    SubjectRevision subjectRevision(Subject subject);

    ConsistencyRevision consistencyRevision(ConsistencyKey key);
}
