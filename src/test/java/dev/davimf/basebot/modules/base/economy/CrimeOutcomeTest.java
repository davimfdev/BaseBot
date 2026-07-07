package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrimeOutcomeTest {
    @Test
    void successAppliesMultToWin() {
        // base 50 + bonus 40 = 90% → roll 10 sucesso; win 200 × 2.5 = 500
        CrimeOutcome o = CrimeOutcome.resolve(10, 50, 40, 0, 200, 2.5, 100, 9999);
        assertTrue(o.success());
        assertEquals(500, o.gain());
        assertEquals(0, o.fine());
    }

    @Test
    void fichaPenaltyLowersChance() {
        // base 50 + 0 − 15 = 35% → roll 40 falha
        CrimeOutcome o = CrimeOutcome.resolve(40, 50, 0, 15, 200, 1.0, 100, 9999);
        assertFalse(o.success());
        assertEquals(100, o.fine());
    }

    @Test
    void fineIsFlooredToCash() {
        // falha, multa rolada 250 mas só tem 30 → multa 30
        CrimeOutcome o = CrimeOutcome.resolve(99, 50, 0, 0, 200, 1.0, 250, 30);
        assertFalse(o.success());
        assertEquals(30, o.fine());
    }

    @Test
    void chanceClampsAt95() {
        // base 50 + bonus 999 → clamp 95; roll 96 falha
        assertFalse(CrimeOutcome.resolve(96, 50, 999, 0, 1, 1.0, 1, 9999).success());
        assertTrue(CrimeOutcome.resolve(94, 50, 999, 0, 1, 1.0, 1, 9999).success());
    }
}
