package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
import java.util.Optional;

/**
 * Point-in-time storage view owned by the caller and closed after the read
 * operation that acquired it.
 */
public interface ReadSnapshot extends AutoCloseable {

    QueryResult read(ReadRequest request);

    Optional<StoredEvent> eventById(Namespace namespace, EventId id);

    SequencePosition highWatermark();

    @Override
    void close();
}
