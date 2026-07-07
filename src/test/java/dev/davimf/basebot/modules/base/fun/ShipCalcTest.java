package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipCalcTest {
    @Test
    void stablePerPairRegardlessOfOrder() {
        assertEquals(ShipCalc.percent("111", "222"), ShipCalc.percent("222", "111"));
    }

    @Test
    void withinZeroToHundred() {
        for (int i = 0; i < 50; i++) {
            int p = ShipCalc.percent("u" + i, "x" + (i * 7));
            assertTrue(p >= 0 && p <= 100, "fora da faixa: " + p);
        }
    }
}
