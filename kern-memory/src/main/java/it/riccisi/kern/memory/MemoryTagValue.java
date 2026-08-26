package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tag;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MemoryTagValue {

    @NonNull private final StoredEvent event;

    @NonNull private final TagName name;

    Optional<TagValue> value() {
        Optional<TagValue> value = Optional.empty();
        for (final Tag tag : this.event.tags()) {
            if (tag.name().equals(this.name)) {
                value = Optional.of(tag.value());
                break;
            }
        }
        return value;
    }
}
