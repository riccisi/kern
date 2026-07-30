package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.ChecksummedBytes;
import java.util.Objects;

public final class StoredEventRecord extends BytesEnvelope {

    public StoredEventRecord(final StoredEvent event) {
        super(
            new ChecksummedBytes(
                new StoredEventRecordContent(Objects.requireNonNull(event, "stored event must not be null"))
            )
        );
    }
}
