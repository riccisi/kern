package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.riccisi.kern.tag.EventTag;
import it.riccisi.kern.tag.EventTags;
import org.junit.jupiter.api.Test;

final class TagsTest {

    @Test
    void treatsTagOrderAsInsignificant() {
        assertEquals(
            new EventTags(new EventTag("studentId", "s9"), new EventTag("courseId", "c7")),
            new EventTags(new EventTag("courseId", "c7"), new EventTag("studentId", "s9"))
        );
    }

    @Test
    void rejectsDuplicateTagNames() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EventTags(new EventTag("courseId", "c7"), new EventTag("courseId", "c8"))
        );
    }

    @Test
    void doesNotEqualArbitraryTagImplementations() {
        assertNotEquals(
            new EventTag("courseId", "c7"),
            new ForeignTag(new TagName("courseId"), new TagValue("c7"))
        );
    }

    private record ForeignTag(TagName name, TagValue value) implements Tag {
    }
}
