package it.riccisi.kern.rocksdb;

import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.value.SequencePosition;
import it.riccisi.kern.core.storage.CommitOutcome;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.FlushMode;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.StorageDiagnostics;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

/**
 * RocksDB-backed implementation of Kern storage.
 */
public final class RocksEventStorage implements EventStorage {
    private final DBOptions options;
    private final RocksDB database;
    private final RocksColumnFamilies families;
    private final RocksTables tables;

    public RocksEventStorage(final Path directory) {
        RocksDB.loadLibrary();
        this.options = new DBOptions()
            .setCreateIfMissing(true)
            .setCreateMissingColumnFamilies(true);
        List<ColumnFamilyHandle> handles = new ArrayList<>();
        try {
            this.database = RocksDB.open(
                this.options,
                Objects.requireNonNull(directory, "storage directory must not be null").toString(),
                RocksColumnFamilies.descriptors(),
                handles
            );
        } catch (RocksDBException exception) {
            this.options.close();
            throw new IllegalStateException("cannot open RocksDB event storage", exception);
        }
        this.families = new RocksColumnFamilies(handles);
        this.tables = new RocksTables(new RocksReader(database, families));
        this.ensureFormatVersion();
    }

    @Override
    public synchronized CommitOutcome commit(final Iterable<PreparedAppend> appends, final Durability durability) {
        Objects.requireNonNull(appends, "prepared appends must not be null");
        Objects.requireNonNull(durability, "durability must not be null");
        List<PreparedAppend> requests = new ArrayList<>();
        appends.forEach(requests::add);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("prepared appends must not be empty");
        }
        if (requests.size() > 1) {
            throw new IllegalArgumentException("group commit requires an overlay and is not enabled");
        }
        try (
            RocksWriteBatch batch = new RocksWriteBatch(families);
            WriteOptions options = new WriteOptions().setSync(durability == Durability.DURABLE)
        ) {
            SequencePosition highWatermark = tables.metadata().highWatermark();
            AppendResult result = new RocksAppend(requests.getFirst(), highWatermark, tables, batch).result();
            tables.metadata().remember(result.toPosition(), batch);
            database.write(options, batch.nativeBatch());
            return new CommitOutcome(List.of(result), result.toPosition());
        } catch (RocksDBException exception) {
            throw new IllegalStateException("cannot commit RocksDB append batch", exception);
        }
    }

    @Override
    public ReadSnapshot snapshot() {
        return new RocksReadSnapshot(database, families);
    }

    @Override
    public StorageDiagnostics diagnostics() {
        return new StorageDiagnostics("RocksDB", tables.metadata().highWatermark(), true, Map.of());
    }

    @Override
    public void flush(final FlushMode mode) {
        Objects.requireNonNull(mode, "flush mode must not be null");
        try (FlushOptions options = new FlushOptions().setWaitForFlush(mode == FlushMode.SYNC)) {
            database.flush(options, families.handles());
        } catch (RocksDBException exception) {
            throw new IllegalStateException("cannot flush RocksDB event storage", exception);
        }
    }

    @Override
    public void close() {
        families.close();
        database.close();
        options.close();
    }

    private void ensureFormatVersion() {
        tables.metadata().verifyFormat();
        if (tables.metadata().formatVersion().isEmpty()) {
            try (
                RocksWriteBatch batch = new RocksWriteBatch(families);
                WriteOptions options = new WriteOptions().setSync(true)
            ) {
                tables.metadata().rememberFormat(batch);
                database.write(options, batch.nativeBatch());
            } catch (RocksDBException exception) {
                throw new IllegalStateException("cannot initialize RocksDB storage format", exception);
            }
        }
    }
}
