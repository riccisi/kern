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
import java.util.Arrays;
import java.util.UUID;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;
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

        assertThat(materialized(key)).isNotEmpty();
    }

    @Test
    void encodesTextComponentsWithLengthPrefix() {
        assertThat(materialized(new NamespaceKey(new Namespace("ab"))))
            .containsExactly((byte) 0, (byte) 0, (byte) 0, (byte) 2, (byte) 'a', (byte) 'b');
    }

    @Test
    void encodesUuidMostSignificantBitsFirst() {
        assertThat(
            materialized(new EventIdKey(
                new Namespace("n"),
                new EventId(UUID.fromString("00000000-0000-0001-0000-000000000002"))
            ))
        ).containsExactly(
            (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 'n',
            (byte) 0x03,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 2
        );
    }

    @Test
    void isolatesNamespacePrefixes() {
        BinaryKey education = new NamespaceKey(new Namespace("education"));
        BinaryKey operations = new NamespaceKey(new Namespace("operations"));

        assertThat(prefixOf(new EventKey(new Namespace("education"), new Position(17)), education))
            .isEqualTo(materialized(education));
        assertThat(prefixOf(new EventKey(new Namespace("operations"), new Position(17)), education))
            .isNotEqualTo(materialized(education));
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
    void comparesKeysByBytesAcrossImplementations() {
        BinaryKey left = new EventKey(new Namespace("education"), new Position(17));
        BinaryKey right = new BinaryKey() {
            @Override
            public byte[] asBytes() {
                return materialized(left);
            }
        };

        assertThat(left).isEqualTo(right);
    }

    @Test
    void coversStorageKeyFamilies() {
        Namespace namespace = new Namespace("education");

        assertThat(materialized(new EventKey(namespace, new Position(1)))).isNotEmpty();
        assertThat(materialized(new SubjectRevisionKey(namespace, new Subject("course:C1"), new SubjectRevision(1)))).isNotEmpty();
        assertThat(materialized(new EventIdKey(namespace, new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1"))))).isNotEmpty();
        assertThat(materialized(new EventTypeKey(namespace, new EventType("EnrollmentConfirmed.v1"), new Position(1)))).isNotEmpty();
        assertThat(materialized(new EventTagKey(namespace, "source", "registration", new Position(1)))).isNotEmpty();
        assertThat(materialized(new SubjectHeadKey(namespace, new Subject("course:C1")))).isNotEmpty();
        assertThat(materialized(new ConsistencyRevisionKey(namespace, new ConsistencyKey("course:C1")))).isNotEmpty();
        assertThat(materialized(new IdempotencyRecordKey(namespace, new IdempotencyKey("append-course-c1-20260729")))).isNotEmpty();
        assertThat(materialized(new SystemKeyBinary(SystemKey.NEXT_POSITION))).isNotEmpty();
    }

    @Test
    void copiesBytesAtBoundary() {
        BinaryKey key = new EventKey(new Namespace("education"), new Position(17));
        byte[] bytes = materialized(key);

        bytes[0] = (byte) (bytes[0] + 1);

        assertThat(materialized(key)[0]).isNotEqualTo(bytes[0]);
    }

    private byte[] prefixOf(BinaryKey key, BinaryKey prefix) {
        return Arrays.copyOf(materialized(key), materialized(prefix).length);
    }

    private byte[] materialized(final Bytes bytes) {
        return new UncheckedBytes(bytes).asBytes();
    }
}
