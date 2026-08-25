package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import it.riccisi.kern.filter.AllEvents;
import it.riccisi.kern.filter.AnyEvents;
import it.riccisi.kern.filter.TaggedAs;
import it.riccisi.kern.filter.TypedBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class EventFilterTest {

    @Test
    void describesCompositeSelection() {
        assertThat(
            "composite filters must describe their semantic tree",
            new AllEvents(
                new AnyEvents(
                    new TypedBy("CourseCreated"),
                    new TypedBy("StudentEnrolled")
                ),
                new TaggedAs("courseId", "c7")
            ).describe(new TextSelection()),
            is(equalTo(
                "all(any(type:CourseCreated,type:StudentEnrolled),tag:courseId=c7)"
            ))
        );
    }

    @Test
    void snapshotsAllCompositionAtConstruction() {
        assertThat(
            "AllEvents must not observe later changes to the external iterable",
            EventFilterTest.descriptionOfAllAfterExternalMutation(),
            is(equalTo("all(type:CourseCreated)"))
        );
    }

    @Test
    void snapshotsAnyCompositionAtConstruction() {
        assertThat(
            "AnyEvents must not observe later changes to the external iterable",
            EventFilterTest.descriptionOfAnyAfterExternalMutation(),
            is(equalTo("any(type:StudentEnrolled)"))
        );
    }

    @Test
    void rejectsNullSelection() {
        assertThat(
            "filters must reject a null selection interpreter",
            EventFilterTest.thrownBy(() -> new TaggedAs("courseId", "c7").describe(null)),
            is(equalTo(NullPointerException.class))
        );
    }

    private static String descriptionOfAllAfterExternalMutation() {
        final List<EventFilter> filters = new ArrayList<>();
        filters.add(new TypedBy("CourseCreated"));
        final AllEvents all = new AllEvents(filters);
        filters.add(new TypedBy("CourseRemoved"));
        return all.describe(new TextSelection());
    }

    private static String descriptionOfAnyAfterExternalMutation() {
        final List<EventFilter> filters = new ArrayList<>();
        filters.add(new TypedBy("StudentEnrolled"));
        final AnyEvents any = new AnyEvents(filters);
        filters.add(new TypedBy("StudentWithdrawn"));
        return any.describe(new TextSelection());
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
