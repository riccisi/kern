package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.cactoos.Text;
import org.cactoos.bytes.BytesOf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class ValueObjectTest {

    @Test
    void acceptsEventTypes() throws Exception {
        assertThat(
            "EventType must accept portable identifier-like text",
            ValueObjectTest.accepted(
                EventType::new,
                "CourseCreated",
                "course.created",
                "course-created.v2"
            ),
            contains(
                "CourseCreated",
                "course.created",
                "course-created.v2"
            )
        );
    }

    @Test
    void rejectsInvalidEventTypes() {
        assertThat(
            "EventType must reject invalid identifier-like text",
            ValueObjectTest.failures(
                EventType::new,
                "1CourseCreated",
                " CourseCreated ",
                " ",
                "C" + "a".repeat(128)
            ),
            everyItem(is(equalTo(IllegalArgumentException.class)))
        );
    }

    @Test
    void acceptsTagNames() throws Exception {
        assertThat(
            "TagName must accept portable identifier-like text",
            ValueObjectTest.accepted(
                TagName::new,
                "courseId",
                "student.id"
            ),
            contains(
                "courseId",
                "student.id"
            )
        );
    }

    @Test
    void rejectsInvalidTagNames() {
        assertThat(
            "TagName must reject invalid identifier-like text",
            ValueObjectTest.failures(
                TagName::new,
                "1courseId",
                " courseId ",
                " ",
                "t" + "a".repeat(64)
            ),
            everyItem(is(equalTo(IllegalArgumentException.class)))
        );
    }

    @Test
    void acceptsNamespaceIds() throws Exception {
        assertThat(
            "NamespaceId must accept portable namespace identifiers",
            ValueObjectTest.accepted(
                NamespaceId::new,
                "default",
                "2026",
                "42",
                "2026-fall",
                "customer_42"
            ),
            contains(
                "default",
                "2026",
                "42",
                "2026-fall",
                "customer_42"
            )
        );
    }

    @Test
    void rejectsInvalidNamespaceIds() {
        assertThat(
            "NamespaceId must reject invalid namespace identifiers",
            ValueObjectTest.failures(
                NamespaceId::new,
                " 2026 ",
                " ",
                "customer:42",
                "n" + "a".repeat(128)
            ),
            everyItem(is(equalTo(IllegalArgumentException.class)))
        );
    }

    @Test
    void acceptsEventIds() throws Exception {
        assertThat(
            "EventId must accept opaque bounded text",
            ValueObjectTest.accepted(
                EventId::new,
                "event-42",
                "course/2026:created#42"
            ),
            contains(
                "event-42",
                "course/2026:created#42"
            )
        );
    }

    @Test
    void rejectsInvalidEventIds() {
        assertThat(
            "EventId must reject invalid opaque text",
            ValueObjectTest.failures(
                EventId::new,
                " event-42 ",
                " ",
                "event\n42",
                "event\r42",
                "event\t42",
                "e".repeat(257)
            ),
            everyItem(is(equalTo(IllegalArgumentException.class)))
        );
    }

    @Test
    void acceptsTagValues() throws Exception {
        assertThat(
            "TagValue must accept opaque bounded Unicode text",
            ValueObjectTest.accepted(
                TagValue::new,
                "Università di Roma",
                "α-42",
                "john.doe@example.com",
                "IT/RM/001"
            ),
            contains(
                "Università di Roma",
                "α-42",
                "john.doe@example.com",
                "IT/RM/001"
            )
        );
    }

    @Test
    void rejectsInvalidTagValues() {
        assertThat(
            "TagValue must reject invalid opaque text",
            ValueObjectTest.failures(
                TagValue::new,
                " Università di Roma ",
                " ",
                "IT\nRM",
                "IT\rRM",
                "IT\tRM",
                "è".repeat(513)
            ),
            everyItem(is(equalTo(IllegalArgumentException.class)))
        );
    }

    @Test
    void rejectsNullValue() {
        assertThat(
            "textual semantic atoms must reject null text",
            ValueObjectTest.nullFailures(),
            everyItem(is(equalTo(NullPointerException.class)))
        );
    }

    @Test
    void keepsTextualAtomsSemanticEquality() {
        assertThat(
            "textual semantic atoms must keep semantic equality",
            ValueObjectTest.equalities(),
            everyItem(is(true))
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

    @Test
    void keepsTextualAtomsHashCodeEquality() {
        assertThat(
            "textual semantic atoms must keep hashCode equality",
            ValueObjectTest.hashEqualities(),
            everyItem(is(true))
        );
    }

    private static List<String> accepted(
        final Function<String, Text> atom,
        final String... origins
    ) throws Exception {
        final List<String> results = new ArrayList<>(origins.length);
        for (final String origin : origins) {
            results.add(
                new String(
                    new BytesOf(atom.apply(origin)).asBytes(),
                    StandardCharsets.UTF_8
                )
            );
        }
        return results;
    }

    private static List<Class<? extends Throwable>> failures(
        final Function<String, Text> atom,
        final String... origins
    ) {
        final List<Class<? extends Throwable>> results = new ArrayList<>(origins.length);
        for (final String origin : origins) {
            results.add(ValueObjectTest.thrownBy(() -> atom.apply(origin)));
        }
        return results;
    }

    private static List<Boolean> equalities() {
        return List.of(
            new EventId("event-42").equals(new EventId("event-42")),
            new EventType("CourseCreated").equals(new EventType("CourseCreated")),
            new NamespaceId("2026").equals(new NamespaceId("2026")),
            new TagName("courseId").equals(new TagName("courseId")),
            new TagValue("Università di Roma").equals(new TagValue("Università di Roma"))
        );
    }

    private static List<Boolean> hashEqualities() {
        return List.of(
            new EventId("event-42").hashCode() == new EventId("event-42").hashCode(),
            new EventType("CourseCreated").hashCode()
                == new EventType("CourseCreated").hashCode(),
            new NamespaceId("2026").hashCode() == new NamespaceId("2026").hashCode(),
            new TagName("courseId").hashCode() == new TagName("courseId").hashCode(),
            new TagValue("Università di Roma").hashCode()
                == new TagValue("Università di Roma").hashCode()
        );
    }

    private static List<Class<? extends Throwable>> nullFailures() {
        return List.of(
            ValueObjectTest.thrownBy(() -> new EventId(null)),
            ValueObjectTest.thrownBy(() -> new EventType(null)),
            ValueObjectTest.thrownBy(() -> new NamespaceId(null)),
            ValueObjectTest.thrownBy(() -> new TagName(null)),
            ValueObjectTest.thrownBy(() -> new TagValue(null))
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
