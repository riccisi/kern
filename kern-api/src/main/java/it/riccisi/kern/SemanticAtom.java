package it.riccisi.kern;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.cactoos.Text;
import org.cactoos.text.UncheckedText;

@RequiredArgsConstructor
public abstract class SemanticAtom implements Text {

    @NonNull private final Text origin;

    @Override
    public String asString() {
        return new UncheckedText(this.origin).asString();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other
            || other != null
            && other.getClass().equals(this.getClass())
            && this.asString().equals(
            this.getClass().cast(other).asString()
        );
    }

    @Override
    public int hashCode() {
        return this.asString().hashCode();
    }

    @Override
    public String toString() {
        return this.asString();
    }
}
