package it.riccisi.kern.rocksdb.record;

import it.riccisi.kern.rocksdb.binary.BytesEnvelope;
import it.riccisi.kern.rocksdb.binary.LongBytes;
import java.time.Instant;
import java.util.Objects;

final class TimestampMicros extends BytesEnvelope {
    private final long epochMicros;

    TimestampMicros(Instant instant) {
        this(epochMicros(instant));
    }

    TimestampMicros(long epochMicros) {
        super(new LongBytes(epochMicros));
        this.epochMicros = epochMicros;
    }

    private static long epochMicros(final Instant instant) {
        Objects.requireNonNull(instant, "timestamp must not be null");
        if (instant.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("recorded at timestamp must use microsecond precision");
        }
        return Math.addExact(
            Math.multiplyExact(instant.getEpochSecond(), 1_000_000L),
            instant.getNano() / 1_000L
        );
    }

    Instant instant() {
        long seconds = Math.floorDiv(epochMicros, 1_000_000L);
        long micros = Math.floorMod(epochMicros, 1_000_000L);
        return Instant.ofEpochSecond(seconds, micros * 1_000L);
    }
}
