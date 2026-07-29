package it.riccisi.kern.api.append;

import it.riccisi.kern.api.value.ConsistencyKey;
import java.util.Map;
import java.util.Objects;

public record ExpectedConsistency(Map<ConsistencyKey, Long> revisions) implements AppendCondition {
    public ExpectedConsistency {
        revisions = Map.copyOf(Objects.requireNonNull(revisions, "consistency revisions must not be null"));
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("consistency revisions must not be empty");
        }
        for (Long revision : revisions.values()) {
            if (revision < 0) {
                throw new IllegalArgumentException("consistency revision must not be negative");
            }
        }
    }
}
