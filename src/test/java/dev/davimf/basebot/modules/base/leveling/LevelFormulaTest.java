package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelFormulaTest {

    @Test
    void xpForLevelUsesMee6Curve() {
        assertEquals(100, LevelFormula.xpForLevel(0));
        assertEquals(155, LevelFormula.xpForLevel(1));
        assertEquals(220, LevelFormula.xpForLevel(2));
    }

    @Test
    void totalXpAccumulates() {
        assertEquals(0, LevelFormula.totalXpForLevel(0));
        assertEquals(100, LevelFormula.totalXpForLevel(1));
        assertEquals(255, LevelFormula.totalXpForLevel(2));
        assertEquals(475, LevelFormula.totalXpForLevel(3));
    }

    @Test
    void levelForXpFindsHighestReached() {
        assertEquals(0, LevelFormula.levelForXp(0));
        assertEquals(0, LevelFormula.levelForXp(99));
        assertEquals(1, LevelFormula.levelForXp(100));
        assertEquals(1, LevelFormula.levelForXp(254));
        assertEquals(2, LevelFormula.levelForXp(255));
    }

    @Test
    void progressWithinLevel() {
        LevelFormula.Progress p = LevelFormula.progress(150);
        assertEquals(1, p.level());
        assertEquals(50, p.into());
        assertEquals(155, p.needed());
    }
}
