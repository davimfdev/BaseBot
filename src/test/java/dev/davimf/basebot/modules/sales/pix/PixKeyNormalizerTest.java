package dev.davimf.basebot.modules.sales.pix;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixKeyNormalizerTest {

    @Test
    void cpfStripsFormattingWhenValid() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("CPF", "529.982.247-25");
        assertTrue(r.ok(), r.error());
        assertEquals("52998224725", r.value());
    }

    @Test
    void cpfRejectsBadCheckDigits() {
        assertFalse(PixKeyNormalizer.normalize("CPF", "111.111.111-11").ok());
        assertFalse(PixKeyNormalizer.normalize("CPF", "123.456.789-00").ok());
    }

    @Test
    void cnpjStripsFormattingWhenValid() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("CNPJ", "11.222.333/0001-81");
        assertTrue(r.ok(), r.error());
        assertEquals("11222333000181", r.value());
    }

    @Test
    void cnpjRejectsBadCheckDigits() {
        assertFalse(PixKeyNormalizer.normalize("CNPJ", "11.222.333/0001-99").ok());
    }

    @Test
    void phoneNormalizesToE164() {
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "62986089609").value());
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "5562986089609").value());
        assertEquals("+5562986089609", PixKeyNormalizer.normalize("PHONE", "+55 (62) 98608-9609").value());
        assertEquals("+556232320000", PixKeyNormalizer.normalize("PHONE", "6232320000").value());
    }

    @Test
    void phoneRejectsTooShortOrLong() {
        assertFalse(PixKeyNormalizer.normalize("PHONE", "123").ok());
        assertFalse(PixKeyNormalizer.normalize("PHONE", "1234567890123456").ok());
    }

    @Test
    void emailLowercasesAndTrims() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("EMAIL", "  Fulano@Example.COM ");
        assertTrue(r.ok(), r.error());
        assertEquals("fulano@example.com", r.value());
    }

    @Test
    void emailRejectsMalformed() {
        assertFalse(PixKeyNormalizer.normalize("EMAIL", "no-at-sign").ok());
    }

    @Test
    void randomAcceptsUuidLowercased() {
        PixKeyNormalizer.Result r = PixKeyNormalizer.normalize("RANDOM", "123E4567-E89B-12D3-A456-426614174000");
        assertTrue(r.ok(), r.error());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", r.value());
    }

    @Test
    void randomRejectsNonUuid() {
        assertFalse(PixKeyNormalizer.normalize("RANDOM", "not-a-uuid").ok());
    }

    @Test
    void unknownTypeFails() {
        assertFalse(PixKeyNormalizer.normalize("XPTO", "abc").ok());
    }
}
