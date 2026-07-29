package it.riccisi.kern.rocksdb.key;

import static org.assertj.core.api.Assertions.assertThat;

import it.riccisi.kern.api.value.ConsistencyKey;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BinaryKeyTest {
    @Test
    void ordersEventKeysByPositionInsideNamespace() {
        Namespace namespace = new Namespace("education");

        assertThat(new EventKey(namespace, new Position(9)))
            .isLessThan(new EventKey(namespace, new Position(10)));
        assertThat(new EventKey(namespace, new Position(10)))
            .isLessThan(new EventKey(namespace, new Position(1_000)));
    }

    @Test
    void ordersSubjectKeysByRevisionInsideSubject() {
        Namespace namespace = new Namespace("education");
        Subject course = new Subject("course:C1");

        assertThat(new SubjectRevisionKey(namespace, course, new SubjectRevision(2)))
            .isLessThan(new SubjectRevisionKey(namespace, course, new SubjectRevision(11)));
    }

    @Test
    void encodesUuidBitsWithoutRejectingSignedLongSegments() {
        BinaryKey key = new EventIdKey(
            new Namespace("education"),
            new EventId(UUID.fromString("f1890f70-7c6a-7d0b-9d01-86de05a9f4b1"))
        );

        assertThat(key.bytes()).isNotEmpty();
    }

    @Test
    void isolatesNamespacePrefixes() {
        BinaryKey education = new NamespaceKey(new Namespace("education"));
        BinaryKey operations = new NamespaceKey(new Namespace("operations"));

        assertThat(new EventKey(new Namespace("education"), new Position(17)).startsWith(education)).isTrue();
        assertThat(new EventKey(new Namespace("operations"), new Position(17)).startsWith(education)).isFalse();
        assertThat(education).isNotEqualTo(operations);
    }

    @Test
    void avoidsSeparatorCollisionsInUtf8Components() {
        Namespace namespace = new Namespace("education");

        BinaryKey split = new EventTagKey(namespace, "a:b", "c", new Position(4));
        BinaryKey shifted = new EventTagKey(namespace, "a", "b:c", new Position(4));
        BinaryKey unicode = new EventTagKey(namespace, "emoji", "value-\u20ac-\uD83D\uDE80", new Position(4));

        assertThat(split).isNotEqualTo(shifted);
        assertThat(unicode).isNotEqualTo(split);
    }

    @Test
    void coversStorageKeyFamilies() {
        Namespace namespace = new Namespace("education");

        assertThat(new EventKey(namespace, new Position(1)).bytes()).isNotEmpty();
        assertThat(new SubjectRevisionKey(namespace, new Subject("course:C1"), new SubjectRevision(1)).bytes()).isNotEmpty();
        assertThat(new EventIdKey(namespace, new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1"))).bytes()).isNotEmpty();
        assertThat(new EventTypeKey(namespace, new EventType("EnrollmentConfirmed.v1"), new Position(1)).bytes()).isNotEmpty();
        assertThat(new EventTagKey(namespace, "source", "registration", new Position(1)).bytes()).isNotEmpty();
        assertThat(new SubjectHeadKey(namespace, new Subject("course:C1")).bytes()).isNotEmpty();
        assertThat(new ConsistencyRevisionKey(namespace, new ConsistencyKey("course:C1")).bytes()).isNotEmpty();
        assertThat(new IdempotencyRecordKey(namespace, new IdempotencyKey("append-course-c1-20260729")).bytes()).isNotEmpty();
        assertThat(new SystemBinaryKey(SystemKey.NEXT_POSITION).bytes()).isNotEmpty();
    }

    @Test
    void copiesBytesAtBoundary() {
        BinaryKey key = new EventKey(new Namespace("education"), new Position(17));
        byte[] bytes = key.bytes();

        bytes[0] = (byte) (bytes[0] + 1);

        assertThat(key.bytes()[0]).isNotEqualTo(bytes[0]);
    }
}
