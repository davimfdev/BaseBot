package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HangmanStateTest {
    @Test
    void correctLetterRevealsNoLifeLost() {
        HangmanState s = HangmanState.start("Coração").guessLetter('C');
        assertTrue(s.masked().startsWith("C"));
        assertEquals(HangmanState.MAX_LIVES, s.lives());
    }

    @Test
    void accentInsensitiveWinsWord() {
        HangmanState s = HangmanState.start("Coração")
                .guessLetter('c').guessLetter('o').guessLetter('r').guessLetter('a');
        assertTrue(s.won());
    }

    @Test
    void wrongLetterLosesLife() {
        HangmanState s = HangmanState.start("gato").guessLetter('z');
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
    }

    @Test
    void repeatedLetterNoExtraPenalty() {
        HangmanState s = HangmanState.start("gato").guessLetter('z').guessLetter('z');
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
    }

    @Test
    void fullWordGuessWinsIgnoringAccentAndCase() {
        HangmanState s = HangmanState.start("Coração").guessWord("coracao");
        assertTrue(s.won());
    }

    @Test
    void wrongFullWordLosesLife() {
        HangmanState s = HangmanState.start("gato").guessWord("cao");
        assertEquals(HangmanState.MAX_LIVES - 1, s.lives());
        assertFalse(s.won());
    }

    @Test
    void lostAfterMaxWrong() {
        HangmanState s = HangmanState.start("gato");
        for (char c : "bcdfhj".toCharArray()) {
            s = s.guessLetter(c);
        }
        assertTrue(s.lost());
    }
}
