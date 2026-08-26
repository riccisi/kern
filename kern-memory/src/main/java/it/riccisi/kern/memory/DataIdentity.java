package it.riccisi.kern.memory;

import it.riccisi.kern.Attribute;
import it.riccisi.kern.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Semantic identity comparison for event data.
 *
 * <p>Data equality is evaluated through metadata and typed attribute values
 * instead of relying on the concrete {@link Data} implementation equality.</p>
 */
@RequiredArgsConstructor
final class DataIdentity {

    @NonNull private final Data data;

    boolean matches(final Data other) {
        return this.data.meta().name().equals(other.meta().name())
            && this.attributes(this.data).equals(this.attributes(other))
            && this.valuesMatch(other);
    }

    private List<AttributeIdentity> attributes(final Data origin) {
        final List<AttributeIdentity> identities = new ArrayList<>();
        for (final Attribute<?> attribute : origin.meta()) {
            identities.add(new AttributeIdentity(attribute));
        }
        return identities;
    }

    private boolean valuesMatch(final Data other) {
        boolean matches = true;
        final var left = this.data.meta().iterator();
        final var right = other.meta().iterator();
        while (left.hasNext() && right.hasNext()) {
            matches = matches && DataIdentity.sameValue(
                this.data,
                left.next(),
                other,
                right.next()
            );
        }
        return matches;
    }

    private static <T> boolean sameValue(
        final Data left,
        final Attribute<T> attribute,
        final Data right,
        final Attribute<?> other
    ) {
        return Objects.equals(
            left.value(attribute),
            DataIdentity.value(right, other)
        );
    }

    private static <T> T value(final Data data, final Attribute<T> attribute) {
        return data.value(attribute);
    }
}
