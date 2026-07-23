package dev.davimf.basebot.modules.facs.recruit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecruitNickTest {

    @Test
    void simpleCase() {
        assertEquals("Joao | 123", RecruitNick.format("Joao", "123"));
    }

    @Test
    void collapsesWhitespaceAndNewlines() {
        assertEquals("Ze da Silva | 99", RecruitNick.format("Ze\n da\t  Silva ", " 99 "));
    }

    @Test
    void emptyNameFallsBackToMembro() {
        assertEquals("Membro | 7", RecruitNick.format("   ", "7"));
    }

    @Test
    void truncatesNameToFit32() {
        String out = RecruitNick.format("NomeMuitoLongoQueEstouraOLimiteDeCaracteres", "12345");
        assertTrue(out.length() <= 32, "len=" + out.length());
        assertTrue(out.endsWith(" | 12345"), out);
    }

    @Test
    void veryLongIdStillBounded() {
        String out = RecruitNick.format("Nome", "1234567890123456789022345678901234");
        assertTrue(out.length() <= 32, "len=" + out.length());
    }
}
