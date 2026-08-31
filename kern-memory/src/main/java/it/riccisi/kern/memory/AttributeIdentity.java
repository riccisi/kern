package it.riccisi.kern.memory;

import it.riccisi.kern.Attribute;

/**
 * Semantic identity of a data attribute used during in-memory event identity
 * comparison.
 */
record AttributeIdentity(String name, Class<?> type) {

    AttributeIdentity(final Attribute<?> attribute) {
        this(attribute.name(), attribute.type());
    }
}
