package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.Subject;
import java.util.Set;

public sealed interface AppendCondition permits AnyAppend, ExpectedConsistency, ExpectedSubjectRevision, NoSubject {

    Set<Subject> observedSubjects();

    Set<ConsistencyKey> observedConsistencyKeys();

    void verify(AppendConditionState state, String diagnosticId);
}
