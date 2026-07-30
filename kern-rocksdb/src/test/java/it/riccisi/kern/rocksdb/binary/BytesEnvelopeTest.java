package it.riccisi.kern.rocksdb.binary;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.cactoos.bytes.BytesOf;
import org.junit.jupiter.api.Test;

final class BytesEnvelopeTest {

    @Test
    void delegatesBytes() {
        assertThatNoException()
            .isThrownBy(
                () -> new BytesEnvelope(
                    new JoinedBytes(
                        new IntBytes(2),
                        new BytesOf((byte) 'a', (byte) 'b')
                    )
                ) {
                }.asBytes()
            );
    }
}
