package it.riccisi.kern.rocksdb;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;

/**
 * Open RocksDB column family handles owned by one storage instance.
 */
final class RocksColumnFamilies implements AutoCloseable {
    private final Map<RocksColumn, ColumnFamilyHandle> handles;

    RocksColumnFamilies(final List<ColumnFamilyHandle> handles) {
        Objects.requireNonNull(handles, "column family handles must not be null");
        if (handles.size() != RocksColumn.values().length) {
            throw new IllegalArgumentException("column family handle count does not match Kern layout");
        }
        this.handles = new EnumMap<>(RocksColumn.class);
        for (RocksColumn column : RocksColumn.values()) {
            this.handles.put(column, handles.get(column.ordinal()));
        }
    }

    static List<ColumnFamilyDescriptor> descriptors() {
        return java.util.Arrays.stream(RocksColumn.values())
            .map(RocksColumn::descriptor)
            .toList();
    }

    ColumnFamilyHandle handle(final RocksColumn column) {
        return handles.get(Objects.requireNonNull(column, "column family must not be null"));
    }

    List<ColumnFamilyHandle> handles() {
        return List.copyOf(handles.values());
    }

    @Override
    public void close() {
        handles.values().forEach(ColumnFamilyHandle::close);
    }
}
