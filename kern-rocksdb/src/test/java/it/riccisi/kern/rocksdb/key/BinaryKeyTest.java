package it.riccisi.kern.rocksdb.key;

import static org.assertj.core.api.Assertions.assertThat;

import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.IdempotencyKey;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import java.util.Arrays;
import java.util.UUID;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;
import org.junit.jupiter.api.Test;

final class BinaryKeyTest {
    @Test
    void ordersEventKeysByPositionInsideNamespace() {
        Namespace namespace = new Namespace("education");

        assertThat(new EventKey(namespace, new SequencePosition(9)))
            .isLessThan(new EventKey(namespace, new SequencePosition(10)));
        assertThat(new EventKey(namespace, new SequencePosition(10)))
            .isLessThan(new EventKey(namespace, new SequencePosition(1_000)));
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
    void isolatesNamespacePrefixes() {
        BinaryKey education = new NamespaceKey(new Namespace("education"));
        BinaryKey operations = new NamespaceKey(new Namespace("operations"));

        assertThat(prefixOf(new EventKey(new Namespace("education"), new SequencePosition(17)), education))
            .isEqualTo(materialized(education));
        assertThat(prefixOf(new EventKey(new Namespace("operations"), new SequencePosition(17)), education))
            .isNotEqualTo(materialized(education));
        assertThat(education).isNotEqualTo(operations);
    }

    @Test
    void avoidsSeparatorCollisionsInTagComponents() {
        Namespace namespace = new Namespace("education");

        BinaryKey split = new EventTagKey(namespace, new EventTag("a:b", "c"), new SequencePosition(4));
        BinaryKey shifted = new EventTagKey(namespace, new EventTag("a", "b:c"), new SequencePosition(4));
        BinaryKey unicode = new EventTagKey(namespace, new EventTag("emoji", "value-\u20ac-\uD83D\uDE80"), new SequencePosition(4));

        assertThat(split).isNotEqualTo(shifted);
        assertThat(unicode).isNotEqualTo(split);
    }

    @Test
    void comparesKeysByBytesAcrossImplementations() {
        BinaryKey left = new EventKey(new Namespace("education"), new SequencePosition(17));
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
        EventTag course = new EventTag("course", "C1");
        EventType type = new EventType("StudentEnrolled.v1");
        SequencePosition position = new SequencePosition(1);

        assertThat(materialized(new EventKey(namespace, position))).isNotEmpty();
        assertThat(materialized(new EventIdKey(namespace, new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1"))))).isNotEmpty();
        assertThat(materialized(new EventTypeKey(namespace, type, position))).isNotEmpty();
        assertThat(materialized(new EventTagKey(namespace, course, position))).isNotEmpty();
        assertThat(materialized(new EventTagTypeKey(namespace, course, type, position))).isNotEmpty();
        assertThat(materialized(new IdempotencyRecordKey(namespace, new IdempotencyKey("append-course-c1-20260729")))).isNotEmpty();
        assertThat(materialized(new SystemKeyBinary(SystemKey.NEXT_POSITION))).isNotEmpty();
    }

    @Test
    void copiesBytesAtBoundary() {
        BinaryKey key = new EventKey(new Namespace("education"), new SequencePosition(17));
        byte[] bytes = materialized(key);

        bytes[0] = (byte) (bytes[0] + 1);

        assertThat(materialized(key)[0]).isNotEqualTo(bytes[0]);
    }

    private byte[] prefixOf(final BinaryKey key, final BinaryKey prefix) {
        return Arrays.copyOf(materialized(key), materialized(prefix).length);
    }

    private byte[] materialized(final Bytes bytes) {
        return new UncheckedBytes(bytes).asBytes();
    }
}
