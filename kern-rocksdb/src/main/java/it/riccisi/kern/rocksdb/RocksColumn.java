package it.riccisi.kern.rocksdb;

import java.nio.charset.StandardCharsets;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.RocksDB;

/**
 * Physical RocksDB column families used by Kern persistent structures.
 */
enum RocksColumn {
    DEFAULT(RocksDB.DEFAULT_COLUMN_FAMILY),
    EVENTS("events"),
    SUBJECT_REVISIONS("subject-revisions"),
    EVENT_IDS("event-ids"),
    TYPES("types"),
    TAGS("tags"),
    SUBJECT_HEADS("subject-heads"),
    CONSISTENCY("consistency"),
    IDEMPOTENCY("idempotency"),
    SYSTEM("system");

    private final byte[] name;

    RocksColumn(final String name) {
        this(name.getBytes(StandardCharsets.UTF_8));
    }

    RocksColumn(final byte[] name) {
        this.name = name.clone();
    }

    ColumnFamilyDescriptor descriptor() {
        return new ColumnFamilyDescriptor(name.clone());
    }
}
