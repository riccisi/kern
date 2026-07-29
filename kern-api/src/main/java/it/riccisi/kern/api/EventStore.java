package it.riccisi.kern.api;

import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import java.util.concurrent.CompletionStage;

public interface EventStore {
    CompletionStage<AppendResult> append(AppendRequest request);
}
