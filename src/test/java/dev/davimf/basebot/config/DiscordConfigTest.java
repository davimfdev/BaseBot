// [OUTLINE START]
// Package: dev.davimf.basebot.config
// 
// Class: DiscordConfigTest
// [OUTLINE END]



package dev.davimf.basebot.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordConfigTest {

    @Test
    void parsesMultipleDevGuildIds() {
        BotConfig.Discord d = new BotConfig.Discord("t", " 111 , 222 ,333,", null);
        assertTrue(d.hasDevGuild());
        assertEquals(List.of("111", "222", "333"), d.devGuildIds());
    }

    @Test
    void singleDevGuildId() {
        assertEquals(List.of("111"), new BotConfig.Discord("t", "111", null).devGuildIds());
    }

    @Test
    void blankMeansNoDevGuild() {
        BotConfig.Discord none = new BotConfig.Discord("t", null, null);
        assertFalse(none.hasDevGuild());
        assertEquals(List.of(), none.devGuildIds());
        assertEquals(List.of(), new BotConfig.Discord("t", "  ", null).devGuildIds());
    }
}
