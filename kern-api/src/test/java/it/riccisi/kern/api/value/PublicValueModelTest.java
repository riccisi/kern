package it.riccisi.kern.api.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PublicValueModelTest {
    @Test
    void acceptsNamespaceSlugWithinByteLimit() {
        assertThat(new Namespace("education-prod-7").value()).isEqualTo("education-prod-7");
    }

    @Test
    void rejectsInvalidNamespaceSlug() {
        assertThatThrownBy(() -> new Namespace("Education_prod"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("namespace must be a lowercase slug");
    }

    @Test
    void rejectsNullNamespace() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Namespace(null))
            .withMessage("namespace must not be null");
    }

    @Test
    void acceptsStableEventType() {
        assertThat(new EventType("StudentEnrolled.v1").value()).isEqualTo("StudentEnrolled.v1");
    }

    @Test
    void rejectsBlankEventType() {
        assertThatThrownBy(() -> new EventType(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event type must not be blank");
    }

    @Test
    void acceptsEventTag() {
        assertThat(new EventTag("student", "S1").toString()).isEqualTo("student:S1");
    }

    @Test
    void rejectsBlankEventTagComponent() {
        assertThatThrownBy(() -> new EventTag("course", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event tag value must not be blank");
    }

    @Test
    void acceptsEventId() {
        UUID value = UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1");

        assertThat(new EventId(value).value()).isEqualTo(value);
    }

    @Test
    void acceptsContentType() {
        assertThat(new ContentType("application/vnd.kern.event+json").value())
            .isEqualTo("application/vnd.kern.event+json");
    }

    @Test
    void rejectsBlankContentType() {
        assertThatThrownBy(() -> new ContentType(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("content type must not be blank");
    }

    @Test
    void acceptsIdempotencyKeyWithinByteLimit() {
        assertThat(new IdempotencyKey("append-2026-07-29:S1:C1").value())
            .isEqualTo("append-2026-07-29:S1:C1");
    }

    @Test
    void rejectsIdempotencyKeyLongerThanUtf8ByteLimit() {
        assertThatThrownBy(() -> new IdempotencyKey("ø".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("idempotency key must not exceed 256 UTF-8 bytes");
    }

    @Test
    void acceptsZeroSequencePositionAsInitialCursor() {
        assertThat(new SequencePosition(0).next()).isEqualTo(new SequencePosition(1));
    }

    @Test
    void rejectsNegativeSequencePosition() {
        assertThatThrownBy(() -> new SequencePosition(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("sequence position must not be negative");
    }

    @Test
    void printsStableTextualValue() {
        assertThat(new Namespace("education").toString()).isEqualTo("education");
    }
}
