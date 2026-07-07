package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EconomyConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(EconomyConfig.enabled(c));
        assertEquals("moedas", EconomyConfig.currencyName(c));
        assertEquals(500, EconomyConfig.daily(c));
        assertEquals(50, EconomyConfig.workMin(c));
        assertEquals(250, EconomyConfig.workMax(c));
        assertEquals(3600, EconomyConfig.workCooldownSeconds(c));
    }

    @Test
    void readsValues() {
        GuildConfig c = cfg(Map.of(EconomyConfig.KEY_CURRENCY_NAME, "dols", EconomyConfig.KEY_DAILY, "1000",
                EconomyConfig.KEY_WORK_MIN, "10", EconomyConfig.KEY_WORK_MAX, "20",
                EconomyConfig.KEY_WORK_COOLDOWN, "120"), Map.of(EconomyConfig.KEY_ENABLED, true));
        assertTrue(EconomyConfig.enabled(c));
        assertEquals("dols", EconomyConfig.currencyName(c));
        assertEquals(1000, EconomyConfig.daily(c));
        assertEquals(10, EconomyConfig.workMin(c));
        assertEquals(20, EconomyConfig.workMax(c));
        assertEquals(120, EconomyConfig.workCooldownSeconds(c));
    }
}
