package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RobOutcomeTest {
    @Test
    void stolenAppliesMultThenCaps() {
        // sucesso: 30% de 100000 = 30000 × 2.5 = 75000, capado no robCap 50000
        RobOutcome o = RobOutcome.resolve(10, 40, 40, 0, 30, 100_000, 2.5, 50_000, 500, 100, 9999);
        assertTrue(o.success());
        assertEquals(50_000, o.stolen());
    }

    @Test
    void stolenRespectsProtectedFloor() {
        // alvo tem 800, floor 500 → stealable 300; 30% de 800 = 240 ×1 = 240 <= 300 → 240
        RobOutcome o = RobOutcome.resolve(0, 40, 5, 0, 30, 800, 1.0, 5_000, 500, 50, 9999);
        assertTrue(o.success());
        assertEquals(240, o.stolen());
    }

    @Test
    void stolenNeverBreaksFloorEvenWithMult() {
        // alvo 600, floor 500 → stealable 100; 30% de 600=180×2.5=450 capado em stealable 100
        RobOutcome o = RobOutcome.resolve(0, 40, 40, 0, 30, 600, 2.5, 50_000, 500, 50, 9999);
        assertEquals(100, o.stolen());
    }

    @Test
    void failFineFlooredToAttackerCash() {
        RobOutcome o = RobOutcome.resolve(99, 40, 0, 0, 30, 100_000, 1.0, 5_000, 500, 200, 40);
        assertFalse(o.success());
        assertEquals(40, o.fine());
        assertEquals(0, o.stolen());
    }
}
