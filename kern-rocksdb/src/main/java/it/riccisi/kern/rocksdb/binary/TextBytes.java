package it.riccisi.kern.rocksdb.binary;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.cactoos.Bytes;
import org.cactoos.bytes.BytesOf;
import org.cactoos.bytes.UncheckedBytes;

public final class TextBytes extends BytesEnvelope {

    private TextBytes(final Bytes bytes) {
        super(
            new JoinedBytes(
                new IntBytes(new UncheckedBytes(bytes).asBytes().length),
                bytes
            )
        );
    }

    public TextBytes(final String text) {
        this(
            new BytesOf(
                Objects.requireNonNull(text, "binary text must not be null")
                    .getBytes(StandardCharsets.UTF_8)
            )
        );
    }
}