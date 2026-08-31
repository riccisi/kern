package it.riccisi.kern.memory;

import it.riccisi.kern.EventStore;
import it.riccisi.kern.conformance.SemanticEventStoreConformanceTest;

final class MemoryEventStoreConformanceTest extends SemanticEventStoreConformanceTest {

    @Override
    protected EventStore store() {
        return new MemoryEventStore();
    }
}
