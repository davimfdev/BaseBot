package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig.EscalationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationConfigTest {

    private static GuildConfig cfg(Map<String, String> settings, Map<String, Boolean> toggles) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), toggles, List.of(), settings);
    }

    @Test
    void parsesRulesWithAndWithoutDuration() {
        List<EscalationRule> rules = ModerationConfig.parse("3=timeout:1h,5=kick,7=ban");
        assertEquals(3, rules.size());
        assertEquals("timeout", rules.get(0).action());
        assertEquals(3_600_000L, rules.get(0).durationMs());
        assertEquals("kick", rules.get(1).action());
        assertNull(rules.get(1).durationMs());
        assertEquals("ban", rules.get(2).action());
    }

    @Test
    void dropsMalformedAndDurationlessTimedActions() {
        // "abc" invalid, "3=foo" unknown action, "4=timeout" missing duration, "9=" empty action.
        List<EscalationRule> rules = ModerationConfig.parse("abc,3=foo,4=timeout,9=,5=ban");
        assertEquals(1, rules.size());
        assertEquals(5, rules.get(0).threshold());
        assertEquals("ban", rules.get(0).action());
    }

    @Test
    void serializeRoundTrips() {
        String raw = "3=timeout:1h,5=kick,7=tempban:2d";
        List<EscalationRule> rules = ModerationConfig.parse(raw);
        String back = ModerationConfig.serialize(rules);
        assertEquals(raw, back);
    }

    @Test
    void escalationForExactThresholdMatch() {
        GuildConfig c = cfg(Map.of(ModerationConfig.KEY_ESCALATION, "3=timeout:1h,5=kick"), Map.of());
        assertNotNull(ModerationConfig.escalationFor(c, 3));
        assertEquals("timeout", ModerationConfig.escalationFor(c, 3).action());
        assertNull(ModerationConfig.escalationFor(c, 4));
        assertEquals("kick", ModerationConfig.escalationFor(c, 5).action());
    }

    @Test
    void defaultsForTtlAndToggles() {
        GuildConfig empty = cfg(Map.of(), Map.of());
        assertEquals(0, ModerationConfig.warnTtlDays(empty));
        assertEquals(0L, ModerationConfig.warnTtlMillis(empty));
        assertTrue(ModerationConfig.dmOnAction(empty));
        assertFalse(ModerationConfig.requireReason(empty));

        GuildConfig set = cfg(Map.of(ModerationConfig.KEY_WARN_TTL, "30"),
                Map.of(ModerationConfig.KEY_DM, false, ModerationConfig.KEY_REQUIRE_REASON, true));
        assertEquals(30, ModerationConfig.warnTtlDays(set));
        assertEquals(30L * 86_400_000L, ModerationConfig.warnTtlMillis(set));
        assertFalse(ModerationConfig.dmOnAction(set));
        assertTrue(ModerationConfig.requireReason(set));
    }
}
