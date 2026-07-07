package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WelcomeConfigTest {
    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(WelcomeConfig.enabled(c));
        assertFalse(WelcomeConfig.dm(c));
        assertFalse(WelcomeConfig.farewellEnabled(c));
        assertEquals(WelcomeConfig.DEFAULT_WELCOME, WelcomeConfig.message(c));
        assertEquals(WelcomeConfig.DEFAULT_FAREWELL, WelcomeConfig.farewellMessage(c));
        assertNull(WelcomeConfig.imageRef(c));
    }

    @Test
    void imageRefSplitsChannelAndMessage() {
        GuildConfig c = cfg(Map.of(WelcomeConfig.KEY_IMAGE, "111:222"), Map.of());
        assertArrayEquals(new String[]{"111", "222"}, WelcomeConfig.imageRef(c));
    }
}
