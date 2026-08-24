package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.iterable.IterableOf;
import org.cactoos.iterable.Mapped;
import org.cactoos.iterable.NoNulls;

/**
 * Composite filter selecting events that match every contained filter.
 */
public final class AllEvents implements EventFilter {

    @NonNull private final Iterable<EventFilter> filters;

    /**
     * Builds a conjunction of event filters.
     *
     * @param filters The filters that must all match.
     */
    public AllEvents(@NonNull final Iterable<EventFilter> filters) {
        this.filters = new NoNulls<>(filters);
    }

    /**
     * Builds a conjunction of event filters.
     *
     * @param filters The filters that must all match.
     */
    public AllEvents(@NonNull final EventFilter... filters) {
        this(new IterableOf<>(filters));
    }

    /**
     * Describes this conjunction to an event-selection interpreter.
     *
     * @param selection The interpretation boundary.
     * @param <T> The representation produced by the interpreter.
     * @return This filter represented as {@code T}.
     */
    @Override
    public <T> T describe(@NonNull final EventSelection<T> selection) {
        return selection.all(
            new Mapped<T>(
                filter -> filter.describe(selection),
                this.filters
            )
        );
    }
}