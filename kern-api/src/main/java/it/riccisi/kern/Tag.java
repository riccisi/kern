package it.riccisi.kern;

/**
 * Application-defined indexed coordinate associated with an {@link Event}.
 */
public interface Tag {

    /**
     * The tag name.
     *
     * @return The tag name.
     */
    TagName name();

    /**
     * The tag value.
     *
     * @return The tag value.
     */
    TagValue value();
}
