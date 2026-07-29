package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.value.Position;
import java.util.Map;
import java.util.Objects;

public record StorageDiagnostics(
    String engine,
    Position highWatermark,
    boolean writable,
    Map<String, String> properties
) {
    public StorageDiagnostics {
        Objects.requireNonNull(engine, "storage engine must not be null");
        if (engine.isBlank()) {
            throw new IllegalArgumentException("storage engine must not be blank");
        }
        Objects.requireNonNull(highWatermark, "high watermark must not be null");
        properties = Map.copyOf(Objects.requireNonNull(properties, "properties must not be null"));
    }
}
