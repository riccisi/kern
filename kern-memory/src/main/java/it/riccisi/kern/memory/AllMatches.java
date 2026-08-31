package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Func;
import org.cactoos.func.UncheckedFunc;
import org.cactoos.iterable.Mapped;
import org.cactoos.scalar.And;
import org.cactoos.scalar.Unchecked;

/**
 * Conjunctive in-memory interpretation of event filter selections.
 */
@RequiredArgsConstructor
final class AllMatches implements Func<StoredEvent, Boolean> {

    @NonNull private final Iterable<? extends Func<StoredEvent, Boolean>> selections;

    @Override
    public Boolean apply(final StoredEvent event) {
        return new Unchecked<>(
            new And(
                new Mapped<>(
                    selection -> () -> selection.apply(event),
                    this.selections
                )
            )
        ).value();
    }
}