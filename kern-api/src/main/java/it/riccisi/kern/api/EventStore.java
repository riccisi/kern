package it.riccisi.kern.api;

import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.api.append.AppendResult;
import it.riccisi.kern.api.query.QueryResult;
import it.riccisi.kern.api.query.ReadRequest;
import java.util.concurrent.CompletionStage;

/**
 * Public event database capability.
 */
public interface EventStore {

    QueryResult read(ReadRequest request);

    CompletionStage<AppendResult> append(AppendRequest request);
}
