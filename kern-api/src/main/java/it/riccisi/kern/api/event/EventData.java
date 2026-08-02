package it.riccisi.kern.api.event;

import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable event payload and its logical associations.
 */
public record EventData(
    EventId id,
    EventType type,
    Set<EventTag> tags,
    ContentType contentType,
    byte[] payload,
    byte[] metadata
) {
    public EventData {
        Objects.requireNonNull(id, "event id must not be null");
        Objects.requireNonNull(type, "event type must not be null");
        tags = Set.copyOf(Objects.requireNonNull(tags, "event tags must not be null"));
        Objects.requireNonNull(contentType, "content type must not be null");
        payload = Objects.requireNonNull(payload, "payload must not be null").clone();
        metadata = Objects.requireNonNull(metadata, "metadata must not be null").clone();
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
    public boolean equals(final Object other) {
        return other instanceof EventData event
            && id.equals(event.id)
            && type.equals(event.type)
            && tags.equals(event.tags)
            && contentType.equals(event.contentType)
            && Arrays.equals(payload, event.payload)
            && Arrays.equals(metadata, event.metadata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, type, tags, contentType);
        result = 31 * result + Arrays.hashCode(payload);
        result = 31 * result + Arrays.hashCode(metadata);
        return result;
    }
}
