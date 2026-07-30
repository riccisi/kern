package it.riccisi.kern.core.append;

import it.riccisi.kern.api.EventStore;
import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.value.Subject;
import it.riccisi.kern.core.storage.CommitOutcome;
import it.riccisi.kern.core.storage.EventStorage;
import it.riccisi.kern.core.storage.PreparedAppend;
import it.riccisi.kern.core.storage.ReadSnapshot;
import it.riccisi.kern.core.storage.RevisionQuery;
import it.riccisi.kern.core.storage.Revisions;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class AppendCoordinator implements EventStore {
    private final EventStorage storage;
    private final RequestDigests digests;
    private final Clock clock;
    private final AtomicLong diagnostics;

    public AppendCoordinator(
        final EventStorage storage,
        final RequestDigests digests,
        final Clock clock
    ) {
        this.storage = Objects.requireNonNull(storage, "event storage must not be null");
        this.digests = Objects.requireNonNull(digests, "request digests must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.diagnostics = new AtomicLong();
    }

    @Override
    public CompletionStage<AppendResult> append(final AppendRequest request) {
        Objects.requireNonNull(request, "append request must not be null");
        try {
            return CompletableFuture.completedFuture(committed(request));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private AppendResult committed(final AppendRequest request) {
        try (ReadSnapshot snapshot = storage.snapshot()) {
            String diagnosticId = diagnosticId();
            AppendResult result = resultFor(request, snapshot, diagnosticId);
            CommitOutcome outcome = storage.commit(
                List.of(new PreparedAppend(
                    request,
                    digests.digest(request),
                    diagnosticId,
                    clock.instant(),
                    result
                )),
                request.durability()
            );
            return outcome.results().getFirst();
        }
    }

    private AppendResult resultFor(
        final AppendRequest request,
        final ReadSnapshot snapshot,
        final String diagnosticId
    ) {
        Set<Subject> subjects = new AppendSubjects(request).asSet();
        Revisions revisions = snapshot.revisions(new RevisionQuery(request.namespace(), subjects, Set.of()));
        new MatchingAppendCondition(request.condition(), revisions, diagnosticId).verify();
        AppendPositionRange positions = new AppendPositionRange(snapshot.highWatermark(), request.events().size());
        return new AppendResult(
            positions.from(),
            positions.to(),
            new AssignedSubjectRevisions(request.events(), revisions).asMap(),
            Map.of(),
            false
        );
    }

    private String diagnosticId() {
        return "append-" + diagnostics.incrementAndGet();
    }
}
