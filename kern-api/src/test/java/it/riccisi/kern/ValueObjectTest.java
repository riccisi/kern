package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
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

    @Test
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new EventId(null));
    }

    @Test
    void keepsRecordEquality() {
        assertEquals(List.of(new EventType("CourseCreated")), List.of(new EventType("CourseCreated")));
    }
}
