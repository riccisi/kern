package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Func;
import org.cactoos.func.UncheckedFunc;

/**
 * Disjunctive in-memory interpretation of event filter selections.
 */
@RequiredArgsConstructor
final class AnyMatches implements Func<StoredEvent, Boolean> {

    @NonNull private final Iterable<? extends Func<StoredEvent, Boolean>> selections;

    @Override
    public Boolean apply(final StoredEvent event) {
        boolean matches = false;
        for (final Func<StoredEvent, Boolean> selection : this.selections) {
            matches = matches || new UncheckedFunc<>(selection).apply(event);
        }
        return matches;
    }
}
