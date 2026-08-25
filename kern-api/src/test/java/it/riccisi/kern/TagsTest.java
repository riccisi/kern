package it.riccisi.kern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import it.riccisi.kern.tag.EventTag;
import it.riccisi.kern.tag.EventTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class TagsTest {

    @Test
    void treatsTagOrderAsInsignificant() {
        assertThat(
            "tag order must not affect EventTags equality",
            new EventTags(new EventTag("courseId", "c7"), new EventTag("studentId", "s9")),
            is(equalTo(new EventTags(new EventTag("studentId", "s9"), new EventTag("courseId", "c7"))))
        );
    }

    @Test
    void rejectsDuplicateTagNames() {
        assertThat(
            "EventTags must reject duplicate tag names",
            TagsTest.thrownBy(
                () -> new EventTags(new EventTag("courseId", "c7"), new EventTag("courseId", "c8"))
            ),
            is(equalTo(IllegalArgumentException.class))
        );
    }

    @Test
    void doesNotEqualArbitraryTagImplementations() {
        assertThat(
            "EventTag equality must remain symmetric by avoiding foreign Tag implementations",
            new EventTag("courseId", "c7"),
            is(not(equalTo(new ForeignTag(new TagName("courseId"), new TagValue("c7")))))
        );
    }

    @Test
    void keepsEventTagRecordEquality() {
        assertThat(
            "EventTag equality must be record value equality",
            new EventTag("courseId", "c7"),
            is(equalTo(new EventTag("courseId", "c7")))
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

    private record ForeignTag(TagName name, TagValue value) implements Tag {
    }
}
