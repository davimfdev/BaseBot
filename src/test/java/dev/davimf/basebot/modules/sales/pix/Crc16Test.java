package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Crc16Test {

    @Test
    void matchesCanonicalCheckValue() {
        // CRC-16/CCITT-FALSE check value for ASCII "123456789" is 0x29B1 (universal vector).
        int crc = Crc16.ccittFalse("123456789".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0x29B1, crc);
    }

    @Test
    void hex4IsUppercaseZeroPadded() {
        assertEquals("29B1", Crc16.hex4("123456789".getBytes(StandardCharsets.US_ASCII)));
    }
}
