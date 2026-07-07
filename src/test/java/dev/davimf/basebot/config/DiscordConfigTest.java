// [OUTLINE START]
// Package: dev.davimf.basebot.config
// 
// Class: DiscordConfigTest
// [OUTLINE END]



package dev.davimf.basebot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordConfigTest {

    @Test
    void hasVaultWhenConfigured() {
        assertTrue(new BotConfig.Discord("t", "1521388944963534869").hasVault());
    }

    @Test
    void noVaultWhenBlank() {
        assertFalse(new BotConfig.Discord("t", null).hasVault());
        assertFalse(new BotConfig.Discord("t", "  ").hasVault());
    }
}
