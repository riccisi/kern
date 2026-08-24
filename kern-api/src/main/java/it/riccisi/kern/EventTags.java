package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.iterable.IterableOf;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable {@link Tags} implementation.
 *
 * <p>A tag name occurs at most once. Iteration preserves construction order,
 * but equality follows the semantic rule that tag order is not significant.</p>
 */
public final class EventTags implements Tags {

    @NonNull private final Iterable<Tag> tags;

    /**
     * Builds tags from an iterable.
     *
     * @param tags The tags associated with an event.
     * @throws NullPointerException When a tag is {@code null}.
     * @throws IllegalArgumentException When a tag name occurs more than once.
     */
    public EventTags(@NonNull final Iterable<? extends Tag> tags) {
        final Map<TagName, Tag> unique = new LinkedHashMap<>();
        for (final Tag tag : tags) {
            final Tag current = Objects.requireNonNull(tag, "Tag must not be null");
            if (unique.putIfAbsent(current.name(), current) != null) {
                throw new IllegalArgumentException("TagName must occur at most once per event");
            }
        }
        this.tags = List.copyOf(unique.values());
    }

    /**
     * Builds tags from varargs.
     *
     * @param tags The tags associated with an event.
     */
    public EventTags(@NonNull final Tag... tags) {
        this(new IterableOf<>(tags));
    }

    /**
     * Creates an iterator over this immutable tag set.
     *
     * @return An iterator over the tags.
     */
    @Override
    public Iterator<Tag> iterator() {
        return this.tags.iterator();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof EventTags that
            && Map.copyOf(this.byName()).equals(Map.copyOf(that.byName()));
    }

    @Override
    public int hashCode() {
        return Map.copyOf(this.byName()).hashCode();
    }

    private Map<TagName, TagValue> byName() {
        final Map<TagName, TagValue> indexed = new LinkedHashMap<>();
        for (final Tag tag : this.tags) {
            indexed.put(tag.name(), tag.value());
        }
        return indexed;
    }
}
