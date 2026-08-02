package it.riccisi.kern.rocksdb.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.event.EventData;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventTag;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.binary.ChecksummedBytes;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.cactoos.Bytes;
import org.cactoos.bytes.BytesOf;
import org.cactoos.bytes.UncheckedBytes;
import org.junit.jupiter.api.Test;

final class StoredEventRecordTest {
    private static final StoredEvent STORED_EVENT = new StoredEvent(
        new Namespace("education"),
        new SequencePosition(42),
        new EventData(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1")),
            new EventType("StudentEnrolled.v1"),
            Set.of(new EventTag("student", "S1"), new EventTag("course", "C1")),
            new ContentType("application/json"),
            "{\"student\":\"S1\",\"course\":\"C1\"}".getBytes(StandardCharsets.UTF_8),
            "{\"correlation\":\"cmd-91\"}".getBytes(StandardCharsets.UTF_8)
        ),
        Instant.parse("2026-07-29T12:34:56.123456Z")
    );

    @Test
    void roundTripsStoredEventsWithOpaquePayloadAndMetadata() {
        byte[] record = materialized(new StoredEventRecord(STORED_EVENT));

        assertThat(new EncodedStoredEventRecord(record).value()).isEqualTo(STORED_EVENT);
    }

    @Test
    void protectsEncodedBytesFromExternalMutation() {
        StoredEventRecord record = new StoredEventRecord(STORED_EVENT);
        byte[] bytes = materialized(record);

        bytes[0] = 0;

        assertThat(new EncodedStoredEventRecord(materialized(record)).value()).isEqualTo(STORED_EVENT);
    }

    @Test
    void rejectsRecordsWithChecksumMismatch() {
        byte[] record = materialized(new StoredEventRecord(STORED_EVENT));
        record[record.length - 8] = (byte) (record[record.length - 8] + 1);

        assertThatThrownBy(() -> new EncodedStoredEventRecord(record).value())
            .isInstanceOf(CorruptEventRecordException.class)
            .hasMessage("event record checksum mismatch");
    }

    @Test
    void rejectsUnsupportedRecordVersions() {
        byte[] record = materialized(new StoredEventRecord(STORED_EVENT));
        record[7] = 2;
        withFreshChecksum(record);

        assertThatThrownBy(() -> new EncodedStoredEventRecord(record).value())
            .isInstanceOf(UnsupportedEventRecordException.class)
            .hasMessage("event record format version 2 is not supported");
    }

    @Test
    void rejectsTruncatedRecords() {
        byte[] record = materialized(new StoredEventRecord(STORED_EVENT));
        byte[] truncated = new byte[record.length - 12];
        System.arraycopy(record, 0, truncated, 0, truncated.length - Integer.BYTES);
        withFreshChecksum(truncated);

        assertThatThrownBy(() -> new EncodedStoredEventRecord(truncated).value())
            .isInstanceOf(CorruptEventRecordException.class)
            .hasMessage("event record is truncated");
    }

    private void withFreshChecksum(final byte[] record) {
        byte[] content = new byte[record.length - Integer.BYTES];
        System.arraycopy(record, 0, content, 0, content.length);
        byte[] fresh = materialized(new ChecksummedBytes(new BytesOf(content)));
        System.arraycopy(fresh, fresh.length - Integer.BYTES, record, record.length - Integer.BYTES, Integer.BYTES);
    }

    private byte[] materialized(final Bytes bytes) {
        return new UncheckedBytes(bytes).asBytes();
    }
}
