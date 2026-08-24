package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
