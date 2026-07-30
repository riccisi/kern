package it.riccisi.kern.core.storage;

import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import java.time.Instant;
import java.util.Objects;

public record PreparedAppend(
    AppendRequest request,
    RequestDigest digest,
    String diagnosticRequestId,
    Instant receivedAt,
    AppendResult result
) {
    public PreparedAppend {
        Objects.requireNonNull(request, "append request must not be null");
        Objects.requireNonNull(digest, "request digest must not be null");
        Objects.requireNonNull(diagnosticRequestId, "diagnostic request id must not be null");
        if (diagnosticRequestId.isBlank()) {
            throw new IllegalArgumentException("diagnostic request id must not be blank");
        }
        Objects.requireNonNull(receivedAt, "received at must not be null");
        Objects.requireNonNull(result, "append result must not be null");
    }
}
