package it.riccisi.kern;

import java.util.concurrent.CompletionStage;

/**
 * Immutable read continuation of a bounded observation.
 *
 * <p>A subscription is pull-based. Calling {@link #next(int)} expresses demand
 * for a future non-empty bounded observation, but it does not advance or mutate
 * the subscription itself. Progress is represented by following the returned
 * {@link StoredEvents}.</p>
 */
public interface Subscription {

    /**
     * Requests the next non-empty bounded observation.
     *
     * <p>The result contains at most {@code count} matching events, ordered by
     * {@link Position}, beginning strictly after the observation watermark from
     * which this subscription was created. If no matching event is available,
     * the returned stage remains pending until at least one appears.</p>
     *
     * @param count The maximum number of matching events to return; must be
     *     greater than zero.
     * @return A stage completed with the next non-empty observation.
     */
    CompletionStage<StoredEvents> next(int count);
}
