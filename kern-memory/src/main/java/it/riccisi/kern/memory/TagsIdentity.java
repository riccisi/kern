package it.riccisi.kern.memory;

import it.riccisi.kern.Tag;
import it.riccisi.kern.TagName;
import it.riccisi.kern.TagValue;
import it.riccisi.kern.Tags;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Semantic identity comparison for event tags.
 *
 * <p>Tags are compared by name-to-value content, independent of iteration
 * order.</p>
 */
@RequiredArgsConstructor
final class TagsIdentity {

    @NonNull private final Tags tags;

    boolean matches(final Tags other) {
        return this.indexed(this.tags).equals(this.indexed(other));
    }

    private Map<TagName, TagValue> indexed(final Tags origin) {
        final Map<TagName, TagValue> indexed = new LinkedHashMap<>();
        for (final Tag tag : origin) {
            indexed.put(tag.name(), tag.value());
        }
        return indexed;
    }
}
