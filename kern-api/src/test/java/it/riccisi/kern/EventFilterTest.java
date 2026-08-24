package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.StringJoiner;
import org.junit.jupiter.api.Test;

final class EventFilterTest {

    @Test
    void describesCompositeSelection() {
        assertEquals(
            "all(any(type:CourseCreated,type:StudentEnrolled),tag:courseId=c7)",
            new AllEvents(
                new AnyEvents(
                    new TypedBy("CourseCreated"),
                    new TypedBy("StudentEnrolled")
                ),
                new TaggedAs("courseId", "c7")
            ).describe(new TextSelection())
        );
    }

    private static final class TextSelection implements EventSelection<String> {

        @Override
        public String all(final Iterable<String> selections) {
            return "all(" + TextSelection.joined(selections) + ")";
        }

        @Override
        public String any(final Iterable<String> selections) {
            return "any(" + TextSelection.joined(selections) + ")";
        }

        @Override
        public String typedBy(final EventType type) {
            return "type:" + type;
        }

        @Override
        public String taggedAs(final Tag tag) {
            return "tag:" + tag;
        }

        private static String joined(final Iterable<String> values) {
            final StringJoiner joined = new StringJoiner(",");
            for (final String value : values) {
                joined.add(value);
            }
            return joined.toString();
        }
    }
}
