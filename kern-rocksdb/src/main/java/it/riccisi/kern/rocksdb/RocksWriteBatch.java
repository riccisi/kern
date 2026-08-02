package it.riccisi.kern.rocksdb;

import java.util.Objects;
import org.cactoos.Bytes;
import org.cactoos.bytes.UncheckedBytes;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;

final class RocksWriteBatch implements AutoCloseable {
    private final WriteBatch origin;
    private final RocksColumnFamilies families;

    RocksWriteBatch(final RocksColumnFamilies families) {
        this.origin = new WriteBatch();
        this.families = Objects.requireNonNull(families, "column families must not be null");
    }

    void put(final RocksColumn column, final Bytes key, final Bytes value) {
        try {
            origin.put(
                families.handle(Objects.requireNonNull(column, "column family must not be null")),
                new UncheckedBytes(Objects.requireNonNull(key, "RocksDB key must not be null")).asBytes(),
                new UncheckedBytes(Objects.requireNonNull(value, "RocksDB value must not be null")).asBytes()
            );
        } catch (RocksDBException exception) {
            throw new IllegalStateException("cannot add RocksDB batch entry", exception);
        }
    }

    WriteBatch nativeBatch() {
        return origin;
    }

    @Override
    public void close() {
        origin.close();
    }
}
