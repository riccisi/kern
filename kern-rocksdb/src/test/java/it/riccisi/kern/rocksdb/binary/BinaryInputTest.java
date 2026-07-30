package it.riccisi.kern.rocksdb.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.cactoos.Bytes;
import org.cactoos.Text;
import org.cactoos.bytes.UncheckedBytes;
import org.cactoos.text.UncheckedText;
import org.junit.jupiter.api.Test;

final class BinaryInputTest {

    @Test
    void readsPrimitiveValuesSequentially() {
        BinaryInput input = new ByteArrayInput(
            new UncheckedBytes(
                new JoinedBytes(
                    new IntBytes(7),
                    new LongBytes(13),
                    new BinaryFieldBytes("payload".getBytes(StandardCharsets.UTF_8))
                )
            ).asBytes()
        );

        assertThat(input.nextInt()).isEqualTo(7);
        assertThat(input.nextLong()).isEqualTo(13);
        Bytes field = new BinaryFieldFromInput(input);

        assertThat(new UncheckedBytes(field).asBytes())
            .containsExactly("payload".getBytes(StandardCharsets.UTF_8));
        assertThatNoException().isThrownBy(input::exhausted);
    }

    @Test
    void copiesSourceBytes() {
        byte[] bytes = new UncheckedBytes(new IntBytes(7)).asBytes();
        BinaryInput input = new ByteArrayInput(bytes);

        bytes[Integer.BYTES - 1] = 8;

        assertThat(input.nextInt()).isEqualTo(7);
    }

    @Test
    void rejectsTrailingBytes() {
        BinaryInput input = new ByteArrayInput(new byte[] {0, 0, 0, 7, 1});

        input.nextInt();

        assertThatThrownBy(input::exhausted)
            .isInstanceOf(MalformedBinaryInputException.class)
            .hasMessage("contains trailing bytes");
    }

    @Test
    void readsUtf8TextAsLengthPrefixedBytes() {
        BinaryInput input = new ByteArrayInput(
            new UncheckedBytes(new TextBytes("città")).asBytes()
        );

        Text text = new TextFromInput(input);

        assertThat(new UncheckedText(text).asString()).isEqualTo("città");
        assertThatNoException().isThrownBy(input::exhausted);
    }
}
