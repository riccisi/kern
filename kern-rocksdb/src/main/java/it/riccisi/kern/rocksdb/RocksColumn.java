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
    EVENT_IDS("event_ids"),
    TAG_TYPE_INDEX("tag_type_index"),
    TYPE_INDEX("type_index"),
    TAG_INDEX("tag_index"),
    IDEMPOTENCY("idempotency"),
    METADATA("metadata"),
    DIAGNOSTICS("diagnostics");

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
