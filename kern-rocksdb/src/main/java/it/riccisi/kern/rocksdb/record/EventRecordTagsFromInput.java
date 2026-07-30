package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.rocksdb.binary.BinaryInput;
import it.riccisi.kern.rocksdb.binary.TextFromInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.cactoos.Scalar;

final class EventRecordTagsFromInput implements Scalar<Map<String, String>> {

    private final BinaryInput input;

    EventRecordTagsFromInput(final BinaryInput input) {
        this.input = Objects.requireNonNull(input, "event record input must not be null");
    }

    @Override
    public Map<String, String> value() {
        int count = input.nextInt();
        if (count < 0) {
            throw new CorruptEventRecordException("event record tag count must not be negative");
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String name = new TextFromInput(input).asString();
            String value = new TextFromInput(input).asString();
            if (tags.put(name, value) != null) {
                throw new CorruptEventRecordException("event record contains duplicate tag " + name);
            }
        }
        return Map.copyOf(tags);
    }
}
