package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelBonusTest {
    @Test
    void noBonusAtLevelZero() {
        assertEquals(100, LevelBonus.scale(100, 0));
    }

    @Test
    void scalesTwoPercentPerLevel() {
        assertEquals(150, LevelBonus.scale(100, 25));
    }

    @Test
    void capsAtLevelFifty() {
        assertEquals(200, LevelBonus.scale(100, 50));
        assertEquals(200, LevelBonus.scale(100, 999));
    }

    @Test
    void negativeLevelTreatedAsZero() {
        assertEquals(100, LevelBonus.scale(100, -5));
    }
}
