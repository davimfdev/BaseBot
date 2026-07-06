// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.pix
// 
// Class: PixPayloadTest
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PixPayloadTest {

    @Test
    void emvEncodesIdLengthValue() {
        assertEquals("000201", PixPayload.emv("00", "01"));
        assertEquals("0014br.gov.bcb.pix", PixPayload.emv("00", "br.gov.bcb.pix"));
    }

    @Test
    void buildsValidStaticBrCode() {
        String code = PixPayload.builder()
                .key("fulano@example.com")
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .txid("***")
                .build()
                .toBrCode();

        assertTrue(code.startsWith("000201"), "payload format indicator");
        assertTrue(code.contains("0014br.gov.bcb.pix"), "pix GUI");
        assertTrue(code.contains("5303986"), "currency BRL 986");
        assertTrue(code.contains("5802BR"), "country BR");
        assertTrue(code.matches(".*6304[0-9A-F]{4}$"), "ends with CRC tag + 4 hex");

        // CRC consistency: recomputing CRC over the body must equal the trailing 4 chars.
        String body = code.substring(0, code.length() - 4);
        String trailer = code.substring(code.length() - 4);
        assertEquals(Crc16.hex4(body.getBytes(StandardCharsets.UTF_8)), trailer);
    }

    @Test
    void includesAmountWithTwoDecimalsWhenPresent() {
        String code = PixPayload.builder()
                .key("k").merchantName("M").merchantCity("C")
                .amount(new BigDecimal("10.5"))
                .build().toBrCode();
        assertTrue(code.contains("540510.50"), "amount tag 54, len 05, value 10.50");
    }

    @Test
    void defaultsTxidToTripleStarWhenBlank() {
        String code = PixPayload.builder()
                .key("k").merchantName("M").merchantCity("C")
                .build().toBrCode();
        assertTrue(code.contains("62070503***"), "additional data field with ref '***'");
    }
}
