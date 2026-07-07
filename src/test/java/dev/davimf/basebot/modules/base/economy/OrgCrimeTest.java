package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrgCrimeTest {
    @Test
    void chanceCombinesBonusAndDirtyPenalty() {
        assertEquals(30 + 20 - 10, OrgCrime.chance(30, 20, 2)); // 40
    }

    @Test
    void chanceClampsTo90And5() {
        assertEquals(90, OrgCrime.chance(30, 100, 0));
        assertEquals(5, OrgCrime.chance(30, 0, 100));
    }

    @Test
    void splitIsProportionalAndSumsExactly() {
        long[] parts = OrgCrime.split(10_000, new double[]{2.5, 1.0, 1.0});
        assertEquals(10_000, parts[0] + parts[1] + parts[2]);
        assertTrue(parts[0] > parts[1]);           // fuzil leva mais
        assertEquals(parts[1], parts[2]);          // pesos iguais → fatias iguais
    }

    @Test
    void splitRemainderGoesToBiggestWeight() {
        // 100 / (1+1+1) = 33 cada, resto 1 → empate no maior peso → primeiro índice leva o resto
        long[] parts = OrgCrime.split(100, new double[]{1.0, 1.0, 1.0});
        assertEquals(34, parts[0]);
        assertEquals(33, parts[1]);
        assertEquals(33, parts[2]);
        assertEquals(100, parts[0] + parts[1] + parts[2]);
    }

    @Test
    void splitRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(-1, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{1.0, -2.0}));
        assertThrows(IllegalArgumentException.class, () -> OrgCrime.split(100, new double[]{1.0, Double.NaN}));
    }
}
