package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void defaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.automod(c));
        assertTrue(SecurityConfig.automodWarn(c));
        assertEquals(1, SecurityConfig.warnPer(c));
        assertEquals(0, SecurityConfig.windowSeconds(c));
        assertEquals(5, SecurityConfig.mentionLimit(c));
        assertTrue(SecurityConfig.blockInvites(c));
        assertTrue(SecurityConfig.keywords(c).isEmpty());
    }

    @Test
    void parsesNumbersAndCsv() {
        GuildConfig c = cfg(Map.of(
                SecurityConfig.KEY_MENTION_LIMIT, "8",
                SecurityConfig.KEY_KEYWORDS, "scam, free nitro ,scam",
                SecurityConfig.KEY_EXEMPT_ROLES, "111,222"
        ), Map.of(SecurityConfig.KEY_AUTOMOD, true));
        assertTrue(SecurityConfig.automod(c));
        assertEquals(8, SecurityConfig.mentionLimit(c));
        assertEquals(List.of("scam", "free nitro"), SecurityConfig.keywords(c));
        assertEquals(Set.of("111", "222"), SecurityConfig.exemptRoleIds(c));
    }

    @Test
    void exemptByRoleOrChannel() {
        GuildConfig c = cfg(Map.of(
                SecurityConfig.KEY_EXEMPT_ROLES, "999",
                SecurityConfig.KEY_EXEMPT_CHANNELS, "777"
        ), Map.of());
        assertTrue(SecurityConfig.isExempt(c, List.of("999"), "100"));
        assertTrue(SecurityConfig.isExempt(c, List.of("1"), "777"));
        assertFalse(SecurityConfig.isExempt(c, List.of("1"), "100"));
    }

    @Test
    void antiRaidDefaults() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.antiraid(c));
        assertEquals(8, SecurityConfig.raidJoins(c));
        assertEquals(10, SecurityConfig.raidWindowSeconds(c));
        assertEquals(7, SecurityConfig.raidMinAgeDays(c));
        assertEquals("HIGH", SecurityConfig.raidLockLevel(c));
        assertNull(SecurityConfig.raidPrevLevel(c));
    }

    @Test
    void verifyDefaultsOffAndTogglesOn() {
        assertFalse(SecurityConfig.verify(cfg(Map.of(), Map.of())));
        assertTrue(SecurityConfig.verify(cfg(Map.of(), Map.of(SecurityConfig.KEY_VERIFY, true))));
    }

    @Test
    void verifyUserSelectDefaultsFalseAndReads() {
        assertFalse(SecurityConfig.verifyUserSelect(cfg(Map.of(), Map.of())));
        assertTrue(SecurityConfig.verifyUserSelect(
                cfg(Map.of(), Map.of(SecurityConfig.KEY_VERIFY_USERSELECT, true))));
    }

    @Test
    void antispamDefaultsFalse() {
        assertFalse(SecurityConfig.antispam(cfg(Map.of(), Map.of())));
        assertTrue(SecurityConfig.antispam(cfg(Map.of(), Map.of(SecurityConfig.KEY_ANTISPAM, true))));
    }

    @Test
    void spamExemptionRules() {
        GuildConfig c = cfg(Map.of(SecurityConfig.KEY_EXEMPT_ROLES, "555"), Map.of());
        assertTrue(SecurityConfig.isSpamExempt(c, true, false, false, List.of()));   // bot
        assertTrue(SecurityConfig.isSpamExempt(c, false, true, false, List.of()));   // owner
        assertTrue(SecurityConfig.isSpamExempt(c, false, false, true, List.of()));   // admin
        assertTrue(SecurityConfig.isSpamExempt(c, false, false, false, List.of("555"))); // cargo isento
        assertFalse(SecurityConfig.isSpamExempt(c, false, false, false, List.of("1")));  // membro comum
    }

    @Test
    void antiNukeDefaultsAndWhitelist() {
        GuildConfig c = cfg(Map.of(), Map.of());
        assertFalse(SecurityConfig.antinuke(c));
        assertEquals(5, SecurityConfig.antinukeMax(c));
        assertEquals(60, SecurityConfig.antinukeWindowSeconds(c));

        GuildConfig wl = cfg(Map.of(SecurityConfig.KEY_ANTINUKE_WHITELIST, "user:42, role:99"), Map.of());
        assertTrue(SecurityConfig.isNukeWhitelisted(wl, "42", List.of()));
        assertTrue(SecurityConfig.isNukeWhitelisted(wl, "7", List.of("99")));
        assertFalse(SecurityConfig.isNukeWhitelisted(wl, "7", List.of("1")));
    }

    @Test
    void badNumberFallsBackToDefault() {
        GuildConfig c = cfg(Map.of(SecurityConfig.KEY_MENTION_LIMIT, "abc"), Map.of());
        assertEquals(5, SecurityConfig.mentionLimit(c));
    }
}
