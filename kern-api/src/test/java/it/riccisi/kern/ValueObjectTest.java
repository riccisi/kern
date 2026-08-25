package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class ValueObjectTest {

    @Test
    void rejectsBlankEventType() {
        assertThat(
            "EventType must reject blank text",
            ValueObjectTest.thrownBy(() -> new EventType(" ")),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsBlankEventId() {
        assertThat(
            "EventId must reject blank text",
            ValueObjectTest.thrownBy(() -> new EventId(" ")),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void preservesSemanticText() {
        assertThat(
            "EventType must expose the semantic text it represents",
            new EventType("CourseCreated").value(),
            is(equalTo("CourseCreated"))
        );
    }

    @Test
    void rejectsBlankNamespace() {
        assertThat(
            "Namespace must reject blank text",
            ValueObjectTest.thrownBy(() -> new Namespace(" ")),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsBlankTagName() {
        assertThat(
            "TagName must reject blank text",
            ValueObjectTest.thrownBy(() -> new TagName(" ")),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsBlankTagValue() {
        assertThat(
            "TagValue must reject blank text",
            ValueObjectTest.thrownBy(() -> new TagValue(" ")),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void rejectsNullValue() {
        assertThat(
            "EventId must reject null text",
            ValueObjectTest.thrownBy(() -> new EventId(null)),
            is(equalTo(NullPointerException.class))
        );
    }

    @Test
    void keepsNamespaceRecordEquality() {
        assertThat(
            "Namespace equality must be record value equality",
            List.of(new Namespace("academic-year-2026")),
            is(equalTo(List.of(new Namespace("academic-year-2026"))))
        );
    }

    @Test
    void keepsRecordEquality() {
        assertThat(
            "EventType equality must be record value equality",
            List.of(new EventType("CourseCreated")),
            is(equalTo(List.of(new EventType("CourseCreated"))))
        );
    }

    private static Class<? extends Throwable> thrownBy(final Executable executable) {
        Class<? extends Throwable> thrown = null;
        try {
            executable.execute();
        } catch (final Throwable failure) {
            thrown = failure.getClass();
        }
        return thrown;
    }
}
