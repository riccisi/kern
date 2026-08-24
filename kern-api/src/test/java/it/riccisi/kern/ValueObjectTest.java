package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ValueObjectTest {

    @Test
    void rejectsBlankEventType() {
        assertThrows(IllegalArgumentException.class, () -> new EventType(" "));
    }

    @Test
    void preservesSemanticText() {
        assertEquals("CourseCreated", new EventType("CourseCreated").value());
    }
}
