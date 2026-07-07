package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChatEventConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles, Map<String, String> channels) {
        return new GuildConfig("g1", null, null, channels, Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of(), Map.of());
        assertFalse(ChatEventConfig.enabled(c));
        assertNull(ChatEventConfig.channelId(c));
        assertEquals(30, ChatEventConfig.minMinutes(c));
        assertEquals(120, ChatEventConfig.maxMinutes(c));
    }

    @Test
    void normalizesRange() {
        GuildConfig c = cfg(Map.of(ChatEventConfig.KEY_MIN, "100", ChatEventConfig.KEY_MAX, "10"),
                Map.of(ChatEventConfig.KEY_ENABLED, true), Map.of(ChatEventConfig.KEY_CHANNEL, "c1"));
        assertTrue(ChatEventConfig.enabled(c));
        assertEquals("c1", ChatEventConfig.channelId(c));
        assertEquals(100, ChatEventConfig.minMinutes(c));
        assertEquals(100, ChatEventConfig.maxMinutes(c));
    }
}
