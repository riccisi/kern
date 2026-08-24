package it.riccisi.kern;

/**
 * A named, typed element of structured {@link Data}.
 *
 * @param <T> The Java type used to read the attribute value.
 */
public interface Attribute<T> {

    /**
     * The attribute name inside its {@link Metadata}.
     *
     * @return The attribute name.
     */
    String name();

    /**
     * The Java type used to read values for this attribute.
     *
     * @return The attribute value type.
     */
    Class<T> type();
}
