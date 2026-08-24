package it.riccisi.kern;

/**
 * A fact from the client domain that is ready to be recorded.
 *
 * <p>An event is complete before persistence. Its identity, type, tags, and
 * data belong to the fact itself; storage coordinates are added only after the
 * event becomes a {@link StoredEvent}.</p>
 */
public interface Event {

    /**
     * The intrinsic identity of this event.
     *
     * <p>The identity exists before persistence and supports safe retry and
     * idempotent append semantics.</p>
     *
     * @return The event identity.
     */
    EventId id();

    /**
     * The semantic kind of fact represented by this event.
     *
     * @return The event type.
     */
    EventType type();

    /**
     * Application-defined indexed coordinates associated with this event.
     *
     * @return The event tags.
     */
    Tags tags();

    /**
     * The structured information carried by this event.
     *
     * @return The event data.
     */
    Data data();
}
