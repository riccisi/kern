package it.riccisi.kern.memory;

import it.riccisi.kern.Conflict;
import it.riccisi.kern.StoredEvent;
import lombok.NonNull;

record MemoryConflict(@NonNull StoredEvent event) implements Conflict {
}
