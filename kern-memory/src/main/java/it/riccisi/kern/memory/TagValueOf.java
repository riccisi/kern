package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import it.riccisi.kern.Tag;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Scalar;

/**
 * Optional value of a tag on a stored event.
 */
@RequiredArgsConstructor
final class TagValueOf implements Scalar<Optional<TagValue>> {

    @NonNull private final StoredEvent event;

    @NonNull private final TagName name;

    @Override
    public Optional<TagValue> value() {
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
