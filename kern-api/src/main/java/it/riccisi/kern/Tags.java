package it.riccisi.kern;

/**
 * Immutable set of event tags.
 *
 * <p>Tags are application metadata, not Kern system metadata. A tag name occurs
 * at most once per event, and tag order is not semantically significant.</p>
 */
public interface Tags extends Iterable<Tag> {
}
