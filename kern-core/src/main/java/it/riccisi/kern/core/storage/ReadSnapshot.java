package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import java.util.Optional;

/**
 * Point-in-time storage view owned by the caller and closed after the read
 * operation that acquired it.
 */
public interface ReadSnapshot extends AutoCloseable {
    EventPage read(EventQuery query);

    Revisions revisions(RevisionQuery query);

    Optional<StoredEvent> eventById(Namespace namespace, EventId id);

    Position highWatermark();

    @Override
    void close();
}
