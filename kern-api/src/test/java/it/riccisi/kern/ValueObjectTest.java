package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.cactoos.Text;
import org.cactoos.bytes.BytesOf;
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
    void composesTextualAtomsAsText() throws Exception {
        assertThat(
            "textual semantic atoms must compose with Cactoos Text boundaries",
            ValueObjectTest.texts(
                new EventId("course-created-7"),
                new EventType("CourseCreated"),
                new NamespaceId("academic-year-2026"),
                new TagName("courseId"),
                new TagValue("c7")
            ),
            contains(
                "course-created-7",
                "CourseCreated",
                "academic-year-2026",
                "courseId",
                "c7"
            )
        );
    }

    @Test
    void rejectsBlankNamespace() {
        assertThat(
            "NamespaceId must reject blank text",
            ValueObjectTest.thrownBy(() -> new NamespaceId(" ")),
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
    void keepsNamespaceIdSemanticEquality() {
        assertThat(
            "NamespaceId equality must be semantic value equality",
            List.of(new NamespaceId("academic-year-2026")),
            is(equalTo(List.of(new NamespaceId("academic-year-2026"))))
        );
    }

    @Test
    void keepsEventTypeSemanticEquality() {
        assertThat(
            "EventType equality must be semantic value equality",
            List.of(new EventType("CourseCreated")),
            is(equalTo(List.of(new EventType("CourseCreated"))))
        );
    }

    @Test
    void keepsTagNameSemanticEquality() {
        assertThat(
            "TagName equality must be semantic value equality",
            List.of(new TagName("courseId")),
            is(equalTo(List.of(new TagName("courseId"))))
        );
    }

    @Test
    void keepsTagValueSemanticEquality() {
        assertThat(
            "TagValue equality must be semantic value equality",
            List.of(new TagValue("c7")),
            is(equalTo(List.of(new TagValue("c7"))))
        );
    }

    @Test
    void keepsSemanticTypeSpecificEquality() {
        assertThat(
            "textual semantic atoms with different semantic types must not be equal",
            new EventId("CourseCreated"),
            is(not(equalTo(new EventType("CourseCreated"))))
        );
    }

    private static List<String> texts(final Text... texts) throws Exception {
        final List<String> results = new ArrayList<>();
        for (final Text text : texts) {
            results.add(new String(new BytesOf(text).asBytes(), StandardCharsets.UTF_8));
        }
        return results;
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
