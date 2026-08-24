package it.riccisi.kern;

/**
 * Description of the structure of {@link Data}.
 *
 * <p>Metadata is distinct from {@link EventType}: the event type identifies the
 * semantic fact, while metadata identifies the shape of the data that describes
 * that fact.</p>
 */
public interface Metadata extends Iterable<Attribute<?>> {

    /**
     * The metadata name.
     *
     * @return The metadata name.
     */
    String name();
}
