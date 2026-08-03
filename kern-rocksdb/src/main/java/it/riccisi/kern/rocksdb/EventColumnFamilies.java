package it.riccisi.kern.rocksdb;

import it.riccisi.kern.core.storage.StoredEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Atomic projection of one committed event into all event-owned column families.
 */
final class EventColumnFamilies implements EventColumnFamily {
    private final List<EventColumnFamily> families;

    EventColumnFamilies(final EventColumnFamily... families) {
        this.families = List.copyOf(Arrays.asList(
            Objects.requireNonNull(families, "event column families must not be null")
        ));
    }

    @Override
    public void remember(final StoredEvent event, final RocksWriteBatch batch) {
        Objects.requireNonNull(event, "stored event must not be null");
        Objects.requireNonNull(batch, "RocksDB batch must not be null");
        families.forEach(family -> family.remember(event, batch));
    }
}
