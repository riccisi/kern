package it.riccisi.kern;

/**
 * Structured information carried by an {@link Event}.
 *
 * <p>Data is a semantic abstraction, not a serialization format. JSON,
 * binary encodings, POJOs, maps, or protocol messages are possible
 * representations outside this contract.</p>
 */
public interface Data {

    /**
     * The structure that describes this data.
     *
     * @return The data metadata.
     */
    Metadata meta();

    /**
     * Reads the value identified by an attribute.
     *
     * @param attribute The attribute to read.
     * @param <T> The expected value type.
     * @return The value associated with the attribute.
     */
    <T> T value(Attribute<T> attribute);
}
