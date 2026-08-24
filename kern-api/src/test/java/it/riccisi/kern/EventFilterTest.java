package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import it.riccisi.kern.filter.AllEvents;
import it.riccisi.kern.filter.AnyEvents;
import it.riccisi.kern.filter.TaggedAs;
import it.riccisi.kern.filter.TypedBy;
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

    @Test
    void snapshotsAllCompositionAtConstruction() {
        final List<EventFilter> filters = new ArrayList<>();
        filters.add(new TypedBy("CourseCreated"));
        final AllEvents all = new AllEvents(filters);
        filters.add(new TypedBy("CourseRemoved"));
        assertEquals("all(type:CourseCreated)", all.describe(new TextSelection()));
    }

    @Test
    void snapshotsAnyCompositionAtConstruction() {
        final List<EventFilter> filters = new ArrayList<>();
        filters.add(new TypedBy("StudentEnrolled"));
        final AnyEvents any = new AnyEvents(filters);
        filters.add(new TypedBy("StudentWithdrawn"));
        assertEquals("any(type:StudentEnrolled)", any.describe(new TextSelection()));
    }

    @Test
    void rejectsNullSelection() {
        assertThrows(NullPointerException.class, () -> new TaggedAs("courseId", "c7").describe(null));
    }

    private static final class TextSelection implements EventSelection<String> {

        @Override
        public String all(final Iterable<? extends String> selections) {
            return "all(" + TextSelection.joined(selections) + ")";
        }

        @Override
        public String any(final Iterable<? extends String> selections) {
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

        private static String joined(final Iterable<? extends String> values) {
            final StringJoiner joined = new StringJoiner(",");
            for (final String value : values) {
                joined.add(value);
            }
            return joined.toString();
        }
    }
}
