package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LevelingConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles, Map<String, String> channels) {
        return new GuildConfig("g1", null, null, channels, Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of(), Map.of());
        assertFalse(LevelingConfig.enabled(c));
        assertEquals("current", LevelingConfig.notifyMode(c));
        assertNull(LevelingConfig.notifyChannelId(c));
        assertTrue(LevelingConfig.ignoredChannels(c).isEmpty());
    }

    @Test
    void readsValues() {
        GuildConfig c = cfg(Map.of(LevelingConfig.KEY_NOTIFY, "dm", LevelingConfig.KEY_IGNORED, "1, 2 ,3"),
                Map.of(LevelingConfig.KEY_ENABLED, true), Map.of(LevelingConfig.KEY_NOTIFY_CHANNEL, "999"));
        assertTrue(LevelingConfig.enabled(c));
        assertEquals("dm", LevelingConfig.notifyMode(c));
        assertEquals("999", LevelingConfig.notifyChannelId(c));
        assertEquals(java.util.Set.of("1", "2", "3"), LevelingConfig.ignoredChannels(c));
    }
}
