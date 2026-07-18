package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VipConfigTest {
    private GuildConfig cfg(Map<String,Boolean> toggles, Map<String,String> settings) {
        return new GuildConfig("g", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test void enabled_defaults_false() {
        assertFalse(VipConfig.enabled(cfg(Map.of(), Map.of())));
        assertTrue(VipConfig.enabled(cfg(Map.of("vip:enabled", true), Map.of())));
    }

    @Test void maxBonus_defaults_100_and_parses() {
        assertEquals(100, VipConfig.maxBonusPct(cfg(Map.of(), Map.of())));
        assertEquals(50, VipConfig.maxBonusPct(cfg(Map.of(), Map.of("vip:max-bonus-pct", "50"))));
        assertEquals(100, VipConfig.maxBonusPct(cfg(Map.of(), Map.of("vip:max-bonus-pct", "lixo"))));
    }
}
