package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.binary.BinaryFieldFromInput;
import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.ByteArrayInput;
import it.riccisi.kern.rocksdb.binary.ChecksummedBinaryInput;
import it.riccisi.kern.rocksdb.binary.MalformedBinaryInputException;
import it.riccisi.kern.rocksdb.binary.TextFromInput;
import java.util.Map;
import java.util.UUID;
import org.cactoos.Scalar;

public final class EncodedStoredEventRecord implements Scalar<StoredEvent> {

    private final BinaryInput input;

    public EncodedStoredEventRecord(final byte[] bytes) {
        this.input = new ChecksummedBinaryInput(new ByteArrayInput(bytes));
    }

    @Override
    public StoredEvent value() {
        try {
            EventRecordFormat.V1.readFrom(input);
            Position position = new Position(input.nextLong());
            SubjectRevision revision = new SubjectRevision(input.nextLong());
            TimestampMicros recordedAt = new TimestampMicros(input.nextLong());
            EventId id = new EventId(new UUID(input.nextLong(), input.nextLong()));
            Namespace namespace = new Namespace(new TextFromInput(input).asString());
            EventType type = new EventType(new TextFromInput(input).asString());
            Subject subject = new Subject(new TextFromInput(input).asString());
            ContentType contentType = new ContentType(new TextFromInput(input).asString());
            SchemaReference schema = new SchemaReference(new TextFromInput(input).asString());
            Map<String, String> tags = new EventRecordTagsFromInput(input).value();
            byte[] metadata = new BinaryFieldFromInput(input).asBytes();
            byte[] payload = new BinaryFieldFromInput(input).asBytes();
            input.exhausted();

            return new StoredEvent(
                namespace,
                position,
                revision,
                new EventData(id, type, subject, contentType, schema, payload, metadata, tags),
                recordedAt.instant()
            );
        } catch (MalformedBinaryInputException exception) {
            throw new CorruptEventRecordException(
                "event record " + exception.getMessage(),
                exception
            );
        }
    }
}
