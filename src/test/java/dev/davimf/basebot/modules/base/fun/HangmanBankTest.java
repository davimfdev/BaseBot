package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HangmanBankTest {

    @Test
    void randomReturnsWordAndTheme() {
        for (int i = 0; i < 50; i++) {
            HangmanBank.Entry e = HangmanBank.random();
            assertNotNull(e);
            assertFalse(e.word().isBlank(), "palavra vazia");
            assertFalse(e.theme().isBlank(), "tema vazio");
        }
    }

    @Test
    void bankCoversSeveralThemes() {
        Set<String> themes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            themes.add(HangmanBank.random().theme());
        }
        assertTrue(themes.size() >= 4, "esperava >=4 temas, veio " + themes.size());
    }
}
