package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.iterable.IterableOf;
import org.cactoos.iterable.Mapped;
import org.cactoos.iterable.NoNulls;

/**
 * Composite filter selecting events that match at least one contained filter.
 */
public final class AnyEvents implements EventFilter {

    @NonNull private final Iterable<EventFilter> filters;

    /**
     * Builds a disjunction of event filters.
     *
     * @param filters The filters of which at least one must match.
     */
    public AnyEvents(@NonNull final Iterable<EventFilter> filters) {
        this.filters = new NoNulls<>(filters);
    }

    /**
     * Builds a disjunction of event filters.
     *
     * @param filters The filters of which at least one must match.
     */
    public AnyEvents(@NonNull final EventFilter... filters) {
        this(new IterableOf<>(filters));
    }

    /**
     * Describes this disjunction to an event-selection interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This filter represented as {@code T}.
     */
    @Override
    public <T> T describe(final EventSelection<T> selection) {
        return selection.any(
            new Mapped<T>(
                filter -> filter.describe(selection),
                this.filters
            )
        );
    }
}
