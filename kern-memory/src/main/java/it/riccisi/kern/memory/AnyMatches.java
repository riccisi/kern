package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Func;
import org.cactoos.iterable.Mapped;
import org.cactoos.scalar.Or;
import org.cactoos.scalar.Unchecked;

/**
 * Disjunctive in-memory interpretation of event filter selections.
 */
@RequiredArgsConstructor
final class AnyMatches implements Func<StoredEvent, Boolean> {

    @NonNull private final Iterable<? extends Func<StoredEvent, Boolean>> selections;

    @Override
    public Boolean apply(final StoredEvent event) {
        return new Unchecked<>(
            new Or(
                new Mapped<>(
                    selection -> () -> selection.apply(event),
                    this.selections
                )
            )
        ).value();
    }
}
