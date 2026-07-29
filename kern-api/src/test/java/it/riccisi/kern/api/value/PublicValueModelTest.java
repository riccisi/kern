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
    void rejectsNamespaceLongerThanUtf8ByteLimit() {
        assertThatThrownBy(() -> new Namespace("a".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("namespace must not exceed 128 UTF-8 bytes");
    }

    @Test
    void acceptsSubjectWithinByteLimit() {
        assertThat(new Subject("course:C1/lesson:42").value()).isEqualTo("course:C1/lesson:42");
    }

    @Test
    void rejectsBlankSubject() {
        assertThatThrownBy(() -> new Subject(" \t"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("subject must not be blank");
    }

    @Test
    void rejectsNullSubject() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Subject(null))
            .withMessage("subject must not be null");
    }

    @Test
    void rejectsSubjectLongerThanUtf8ByteLimit() {
        assertThatThrownBy(() -> new Subject("é".repeat(257)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("subject must not exceed 512 UTF-8 bytes");
    }

    @Test
    void acceptsStableEventType() {
        assertThat(new EventType("EnrollmentConfirmed.v1").value()).isEqualTo("EnrollmentConfirmed.v1");
    }

    @Test
    void rejectsBlankEventType() {
        assertThatThrownBy(() -> new EventType(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event type must not be blank");
    }

    @Test
    void rejectsNullEventType() {
        assertThatNullPointerException()
            .isThrownBy(() -> new EventType(null))
            .withMessage("event type must not be null");
    }

    @Test
    void acceptsEventId() {
        UUID value = UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1");

        assertThat(new EventId(value).value()).isEqualTo(value);
    }

    @Test
    void rejectsNullEventId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new EventId(null))
            .withMessage("event id must not be null");
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
    void rejectsNullContentType() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ContentType(null))
            .withMessage("content type must not be null");
    }

    @Test
    void acceptsSchemaReference() {
        assertThat(new SchemaReference("schema://education/enrollment-confirmed/1").value())
            .isEqualTo("schema://education/enrollment-confirmed/1");
    }

    @Test
    void rejectsBlankSchemaReference() {
        assertThatThrownBy(() -> new SchemaReference("\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("schema reference must not be blank");
    }

    @Test
    void rejectsNullSchemaReference() {
        assertThatNullPointerException()
            .isThrownBy(() -> new SchemaReference(null))
            .withMessage("schema reference must not be null");
    }

    @Test
    void acceptsConsistencyKey() {
        assertThat(new ConsistencyKey("enrollment:S1:C1").value()).isEqualTo("enrollment:S1:C1");
    }

    @Test
    void rejectsBlankConsistencyKey() {
        assertThatThrownBy(() -> new ConsistencyKey(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("consistency key must not be blank");
    }

    @Test
    void rejectsNullConsistencyKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ConsistencyKey(null))
            .withMessage("consistency key must not be null");
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
    void rejectsNullIdempotencyKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> new IdempotencyKey(null))
            .withMessage("idempotency key must not be null");
    }

    @Test
    void acceptsZeroPositionAsInitialCursor() {
        assertThat(new Position(0).next()).isEqualTo(new Position(1));
    }

    @Test
    void rejectsNegativePosition() {
        assertThatThrownBy(() -> new Position(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("position must not be negative");
    }

    @Test
    void acceptsZeroSubjectRevisionAsEmptySubject() {
        assertThat(new SubjectRevision(0).next()).isEqualTo(new SubjectRevision(1));
    }

    @Test
    void rejectsNegativeSubjectRevision() {
        assertThatThrownBy(() -> new SubjectRevision(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("subject revision must not be negative");
    }

    @Test
    void preservesEqualityByWrappedValue() {
        assertThat(new ConsistencyKey("course:C1")).isEqualTo(new ConsistencyKey("course:C1"));
    }

    @Test
    void printsStableTextualValue() {
        assertThat(new Namespace("education").toString()).isEqualTo("education");
    }
}
