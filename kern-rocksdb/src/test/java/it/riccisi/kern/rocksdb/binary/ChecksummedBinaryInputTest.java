package it.riccisi.kern.rocksdb.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.cactoos.bytes.UncheckedBytes;
import org.junit.jupiter.api.Test;

final class ChecksummedBinaryInputTest {

    @Test
    void verifiesConsumedBinaryContent() {
        BinaryInput input = input(
            new ChecksummedBytes(
                new JoinedBytes(
                    new IntBytes(37),
                    new LongBytes(91),
                    new BinaryFieldBytes("payload".getBytes(StandardCharsets.UTF_8))
                )
            )
        );

        assertThat(input.nextInt()).isEqualTo(37);
        assertThat(input.nextLong()).isEqualTo(91);
        assertThat(new UncheckedBytes(new BinaryFieldFromInput(input)).asBytes())
            .containsExactly("payload".getBytes(StandardCharsets.UTF_8));
        assertThatNoException().isThrownBy(input::exhausted);
    }

    @Test
    void rejectsMismatchedChecksums() {
        byte[] binary = new UncheckedBytes(new ChecksummedBytes(new IntBytes(37))).asBytes();
        binary[0] = (byte) (binary[0] + 1);
        BinaryInput input = new ChecksummedBinaryInput(new ByteArrayInput(binary));

        input.nextInt();

        assertThatThrownBy(input::exhausted)
            .isInstanceOf(MalformedBinaryInputException.class)
            .hasMessage("checksum mismatch");
    }

    private BinaryInput input(final ChecksummedBytes binary) {
        return new ChecksummedBinaryInput(
            new ByteArrayInput(new UncheckedBytes(binary).asBytes())
        );
    }
}
