package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.rocksdb.binary.BinaryFieldBytes;
import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.JoinedBytes;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import it.riccisi.kern.rocksdb.binary.TextBytes;
import it.riccisi.kern.rocksdb.binary.UuidBytes;
import it.riccisi.kern.core.storage.StoredEvent;
import java.util.Objects;

final class StoredEventRecordContent extends BytesEnvelope {

    StoredEventRecordContent(final StoredEvent event) {
        super(
            new JoinedBytes(
                EventRecordFormat.V1,
                new LongBytes(Objects.requireNonNull(event, "stored event must not be null").position().value()),
                new TimestampMicros(event.recordedAt()),
                new UuidBytes(event.data().id().value()),
                new TextBytes(event.namespace().value()),
                new TextBytes(event.data().type().value()),
                new TextBytes(event.data().contentType().value()),
                new EventRecordTags(event.data().tags()),
                new BinaryFieldBytes(event.data().metadata()),
                new BinaryFieldBytes(event.data().payload())
            )
        );
    }
}
