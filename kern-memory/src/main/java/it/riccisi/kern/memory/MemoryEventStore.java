package it.riccisi.kern.memory;

import it.riccisi.kern.EventFilter;
import it.riccisi.kern.EventStore;
import it.riccisi.kern.NamespaceId;
import it.riccisi.kern.Position;
import it.riccisi.kern.StoredEvents;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;

/**
 * In-memory semantic reference implementation of {@link EventStore}.
 *
 * <p>The store keeps independent namespace histories in process memory and is
 * intended for conformance, tests, and simple local usage rather than durable
 * persistence.</p>
 */
public final class MemoryEventStore implements EventStore {

    private final Map<NamespaceId, MemoryNamespace> namespaces;

    public MemoryEventStore() {
        this.namespaces = new LinkedHashMap<>();
    }

    @Override
    public synchronized StoredEvents events(
        @NonNull final NamespaceId namespace,
        @NonNull final EventFilter filter,
        @NonNull final Position after
    ) {
        return this.namespaces
            .computeIfAbsent(namespace, ignored -> new MemoryNamespace()).observe(filter, after);
    }

}
