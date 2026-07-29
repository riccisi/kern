package it.riccisi.kern.api.append;

public sealed interface AppendCondition permits AnyAppend, ExpectedConsistency, ExpectedSubjectRevision, NoSubject {
}
