package it.riccisi.kern;

import lombok.NonNull;
import org.cactoos.Text;

/**
 * Semantic kind of fact represented by an {@link Event}.
 *
 * <p>The event type answers what happened. It is distinct from
 * {@link Metadata}, which describes the structure of the event data.</p>
 */
public final class EventType extends SemanticAtom {

    private static final int MAXIMUM_LENGTH = 128;

    public EventType(@NonNull final String origin) {
        super(
            new Identifier(
                origin,
                EventType.MAXIMUM_LENGTH,
                "EventType"
            )
        );
    }
}
