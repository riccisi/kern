package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.TextFromInput;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.cactoos.Scalar;

final class EventRecordTagsFromInput implements Scalar<Set<EventTag>> {

    private final BinaryInput input;

    EventRecordTagsFromInput(final BinaryInput input) {
        this.input = Objects.requireNonNull(input, "event record input must not be null");
    }

    @Override
    public Set<EventTag> value() {
        int count = input.nextInt();
        if (count < 0) {
            throw new CorruptEventRecordException("event record tag count must not be negative");
        }
        Set<EventTag> tags = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String name = new TextFromInput(input).asString();
            String value = new TextFromInput(input).asString();
            EventTag tag = new EventTag(name, value);
            if (!tags.add(tag)) {
                throw new CorruptEventRecordException("event record contains duplicate tag " + tag);
            }
        }
        return Set.copyOf(tags);
    }
}
