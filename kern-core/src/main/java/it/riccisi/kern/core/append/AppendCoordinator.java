package it.riccisi.kern.core.append;

import it.riccisi.kern.api.EventStore;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.append.Durability;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AppendCoordinator implements EventStore {
    private final EventStorage storage;
    private final RequestDigests digests;
    private final Clock clock;
    private final DiagnosticIds diagnostics;

    public AppendCoordinator(
        final EventStorage storage,
        final RequestDigests digests,
        final Clock clock,
        final DiagnosticIds diagnostics
    ) {
        this.storage = Objects.requireNonNull(storage, "event storage must not be null");
        this.digests = Objects.requireNonNull(digests, "request digests must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostic ids must not be null");
    }

    @Override
    public QueryResult read(final ReadRequest request) {
        Objects.requireNonNull(request, "read request must not be null");
        try (ReadSnapshot snapshot = storage.snapshot()) {
            return snapshot.read(request);
        }
    }

    @Override
    public CompletionStage<AppendResult> append(final AppendRequest request) {
        Objects.requireNonNull(request, "append request must not be null");
        try {
            PreparedAppend append = new PreparedAppend(
                request,
                digests.digest(request),
                diagnostics.next(),
                clock.instant()
            );
            return CompletableFuture.completedFuture(
                storage.commit(List.of(append), Durability.DURABLE).onlyResult()
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
