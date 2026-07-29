package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record EventData(
    EventId id,
    EventType type,
    Subject subject,
    ContentType contentType,
    SchemaReference schema,
    byte[] payload,
    byte[] metadata,
    Map<String, String> tags
) {
    public EventData {
        Objects.requireNonNull(id, "event id must not be null");
        Objects.requireNonNull(type, "event type must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(contentType, "content type must not be null");
        Objects.requireNonNull(schema, "schema reference must not be null");
        payload = Objects.requireNonNull(payload, "payload must not be null").clone();
        metadata = Objects.requireNonNull(metadata, "metadata must not be null").clone();
        tags = Map.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public byte[] metadata() {
        return metadata.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EventData event
            && id.equals(event.id)
            && type.equals(event.type)
            && subject.equals(event.subject)
            && contentType.equals(event.contentType)
            && schema.equals(event.schema)
            && Arrays.equals(payload, event.payload)
            && Arrays.equals(metadata, event.metadata)
            && tags.equals(event.tags);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, type, subject, contentType, schema, tags);
        result = 31 * result + Arrays.hashCode(payload);
        result = 31 * result + Arrays.hashCode(metadata);
        return result;
    }
}
