package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Subject;

/**
 * Predicate over event subjects used by read queries.
 */
public sealed interface SubjectFilter permits AllSubjects, SingleSubject {

    boolean accepts(Subject subject);
}
