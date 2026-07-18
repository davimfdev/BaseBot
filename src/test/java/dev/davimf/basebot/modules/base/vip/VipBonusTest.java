package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VipBonusTest {
    @Test void scale_floors_down() {
        assertEquals(125, VipBonus.scale(100, 25));
        assertEquals(126, VipBonus.scale(101, 25)); // 101 + floor(2525/100=25) = 126
        assertEquals(100, VipBonus.scale(100, 0));
    }

    @Test void scale_ignores_negative_pct() {
        assertEquals(100, VipBonus.scale(100, -10));
    }

    @Test void cap_limits_pct() {
        assertEquals(100, VipBonus.cap(250, 100));
        assertEquals(40, VipBonus.cap(40, 100));
        assertEquals(0, VipBonus.cap(-5, 100));
    }
}
