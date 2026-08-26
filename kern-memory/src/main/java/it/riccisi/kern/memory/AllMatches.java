package it.riccisi.kern.memory;

import it.riccisi.kern.StoredEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Func;
import org.cactoos.func.UncheckedFunc;

/**
 * Conjunctive in-memory interpretation of event filter selections.
 */
@RequiredArgsConstructor
final class AllMatches implements Func<StoredEvent, Boolean> {

    @NonNull private final Iterable<? extends Func<StoredEvent, Boolean>> selections;

    @Override
    public Boolean apply(final StoredEvent event) {
        boolean matches = true;
        for (final Func<StoredEvent, Boolean> selection : this.selections) {
            matches = matches && new UncheckedFunc<>(selection).apply(event);
        }
        return matches;
    }
}
