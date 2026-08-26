package it.riccisi.kern.memory;

import it.riccisi.kern.Conflict;
import it.riccisi.kern.StoredEvent;
import lombok.NonNull;

/**
 * Tail conflict caused by a stored event relevant to the original observation.
 */
record MemoryConflict(@NonNull StoredEvent event) implements Conflict {
}
