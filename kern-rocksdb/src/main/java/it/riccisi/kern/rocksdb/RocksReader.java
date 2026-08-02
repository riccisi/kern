package it.riccisi.kern.rocksdb;

import java.util.Objects;
import java.util.Optional;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

/**
 * Read access to RocksDB using an optional native snapshot.
 */
final class RocksReader {
    private final RocksDB database;
    private final RocksColumnFamilies families;
    private final Optional<ReadOptions> options;

    RocksReader(final RocksDB database, final RocksColumnFamilies families) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.families = Objects.requireNonNull(families, "column families must not be null");
        this.options = Optional.empty();
    }

    RocksReader(final RocksDB database, final RocksColumnFamilies families, final ReadOptions options) {
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.families = Objects.requireNonNull(families, "column families must not be null");
        this.options = Optional.of(Objects.requireNonNull(options, "read options must not be null"));
    }

    Optional<byte[]> bytes(final RocksColumn column, final Bytes key) {
        Objects.requireNonNull(column, "column family must not be null");
        Objects.requireNonNull(key, "RocksDB key must not be null");
        try {
            byte[] raw = new UncheckedBytes(key).asBytes();
            if (options.isPresent()) {
                return Optional.ofNullable(database.get(families.handle(column), options.get(), raw));
            }
            return Optional.ofNullable(database.get(families.handle(column), raw));
        } catch (RocksDBException exception) {
            throw new IllegalStateException("cannot read RocksDB value", exception);
        }
    }
}
