package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import it.riccisi.kern.filter.TypedBy;
import it.riccisi.kern.reduction.Excluding;
import it.riccisi.kern.reduction.Latest;
import it.riccisi.kern.reduction.LatestBy;
import it.riccisi.kern.reduction.Matching;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class EventReductionTest {

    @Test
    void describesLatest() {
        assertThat(
            "latest reduction must select the latest event",
            new Latest().describe(new TextReductionSelection()),
            is(equalTo("latest"))
        );
    }

    @Test
    void describesLatestBy() {
        assertThat(
            "latest-by reduction must select the latest event for a tag name",
            new LatestBy(new TagName("courseId")).describe(new TextReductionSelection()),
            is(equalTo("latestBy:courseId"))
        );
    }

    @Test
    void describesMatching() {
        assertThat(
            "matching reduction must delegate to the provided event filter",
            new Matching(new TypedBy("CourseCreated")).describe(new TextReductionSelection()),
            is(equalTo("matching:type:CourseCreated"))
        );
    }

    @Test
    void describesExcluding() {
        assertThat(
            "excluding reduction must delegate to the provided event filter",
            new Excluding(new TypedBy("CourseRemoved")).describe(new TextReductionSelection()),
            is(equalTo("excluding:type:CourseRemoved"))
        );
    }

    @Test
    void rejectsNullReductionSelection() {
        assertThat(
            "reductions must reject a null selection interpreter",
            EventReductionTest.thrownBy(() -> new Latest().describe(null)),
            is(equalTo(NullPointerException.class))
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

    private static final class TextReductionSelection implements EventReductionSelection<String> {

        @Override
        public String latest() {
            return "latest";
        }

        @Override
        public String latestBy(final TagName tag) {
            return "latestBy:" + tag;
        }

        @Override
        public String matching(final EventFilter filter) {
            return "matching:" + filter.describe(new TextEventSelection());
        }

        @Override
        public String excluding(final EventFilter filter) {
            return "excluding:" + filter.describe(new TextEventSelection());
        }
    }

    private static final class TextEventSelection implements EventSelection<String> {

        @Override
        public String all(final Iterable<? extends String> selections) {
            return String.join(",", selections);
        }

        @Override
        public String any(final Iterable<? extends String> selections) {
            return String.join(",", selections);
        }

        @Override
        public String typedBy(final EventType type) {
            return "type:" + type;
        }

        @Override
        public String taggedAs(final Tag tag) {
            return "tag:" + tag;
        }
    }
}
