package it.riccisi.kern.rocksdb.binary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class LongFromBytesTest {
    @Test
    void readsBigEndianLong() throws Exception {
        assertThat(new LongFromBytes(new LongBytes(0x0102030405060708L).asBytes()).value())
            .isEqualTo(0x0102030405060708L);
    }
}
