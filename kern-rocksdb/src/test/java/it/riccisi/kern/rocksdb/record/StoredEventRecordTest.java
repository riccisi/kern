package it.riccisi.kern.rocksdb.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.riccisi.kern.api.append.EventData;
import it.riccisi.kern.api.value.ContentType;
import it.riccisi.kern.api.value.EventId;
import it.riccisi.kern.api.value.EventType;
import it.riccisi.kern.api.value.Namespace;
import it.riccisi.kern.api.value.Position;
import it.riccisi.kern.api.value.SchemaReference;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.api.value.SubjectRevision;
import it.riccisi.kern.core.storage.StoredEvent;
import it.riccisi.kern.rocksdb.binary.ChecksummedBytes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.cactoos.Bytes;
import org.cactoos.bytes.BytesOf;
import org.cactoos.bytes.UncheckedBytes;
import org.junit.jupiter.api.Test;

final class StoredEventRecordTest {
    private static final StoredEvent STORED_EVENT = new StoredEvent(
        new Namespace("education"),
        new Position(42),
        new SubjectRevision(7),
        new EventData(
            new EventId(UUID.fromString("01890f70-7c6a-7d0b-9d01-86de05a9f4b1")),
            new EventType("EnrollmentRegistered.v1"),
            new Subject("enrollment:E-2026-07-29"),
            new ContentType("application/json"),
            new SchemaReference("schema:enrollment-registered:1"),
            "{\"student\":\"S1\",\"course\":\"C1\"}".getBytes(StandardCharsets.UTF_8),
            "{\"correlation\":\"cmd-91\"}".getBytes(StandardCharsets.UTF_8),
            Map.of("course", "C1", "source", "registration")
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

    @Test
    void decodesCommittedV1Fixture() {
        byte[] fixture = HexFormat.of().parseHex(fixture("event-record-v1.hex"));

        assertThat(new EncodedStoredEventRecord(fixture).value()).isEqualTo(STORED_EVENT);
    }

    @Test
    void keepsCommittedFixtureStable() {
        byte[] fixture = HexFormat.of().parseHex(fixture("event-record-v1.hex"));

        assertThat(materialized(new StoredEventRecord(STORED_EVENT))).isEqualTo(fixture);
    }

    private String fixture(String name) {
        try (var input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void withFreshChecksum(byte[] record) {
        byte[] content = new byte[record.length - Integer.BYTES];
        System.arraycopy(record, 0, content, 0, content.length);
        byte[] fresh = materialized(new ChecksummedBytes(new BytesOf(content)));
        System.arraycopy(fresh, fresh.length - Integer.BYTES, record, record.length - Integer.BYTES, Integer.BYTES);
    }

    private byte[] materialized(final Bytes bytes) {
        return new UncheckedBytes(bytes).asBytes();
    }
}
