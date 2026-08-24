package it.riccisi.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.riccisi.kern.filter.TypedBy;
import it.riccisi.kern.reduction.Excluding;
import it.riccisi.kern.reduction.Latest;
import it.riccisi.kern.reduction.LatestBy;
import it.riccisi.kern.reduction.Matching;
import org.junit.jupiter.api.Test;

final class EventReductionTest {

    @Test
    void describesLatest() {
        assertEquals("latest", new Latest().describe(new TextReductionSelection()));
    }

    @Test
    void describesLatestBy() {
        assertEquals(
            "latestBy:courseId",
            new LatestBy(new TagName("courseId")).describe(new TextReductionSelection())
        );
    }

    @Test
    void describesMatching() {
        assertEquals(
            "matching:type:CourseCreated",
            new Matching(new TypedBy("CourseCreated")).describe(new TextReductionSelection())
        );
    }

    @Test
    void describesExcluding() {
        assertEquals(
            "excluding:type:CourseRemoved",
            new Excluding(new TypedBy("CourseRemoved")).describe(new TextReductionSelection())
        );
    }

    @Test
    void rejectsNullReductionSelection() {
        assertThrows(NullPointerException.class, () -> new Latest().describe(null));
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
