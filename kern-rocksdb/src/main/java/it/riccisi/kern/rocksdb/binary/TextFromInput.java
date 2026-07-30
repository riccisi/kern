package it.riccisi.kern.rocksdb.binary;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.cactoos.Text;

public final class TextFromInput implements Text {

    private final BinaryInput input;

    public TextFromInput(final BinaryInput input) {
        this.input = Objects.requireNonNull(input, "binary input must not be null");
    }

    @Override
    public String asString() {
        return new String(
            new BinaryFieldFromInput(input).asBytes(),
            StandardCharsets.UTF_8
        );
    }
}
