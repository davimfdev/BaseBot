package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoiceScopeTest {

    private static GuildConfig cfg(Map<String, String> settings) {
        return new GuildConfig("g1", null, null, Map.of(), Map.of(), Map.of(), List.of(), settings);
    }

    private static final GuildConfig EMPTY = cfg(Map.of());

    @Test
    void withoutListsFallsBackToPublicDefault() {
        assertTrue(VoiceScope.counts("chan", "cat", true, EMPTY));
        assertFalse(VoiceScope.counts("chan", "cat", false, EMPTY));
    }

    @Test
    void channelWithoutCategoryFallsBackToPublicDefault() {
        assertTrue(VoiceScope.counts("chan", null, true, EMPTY));
        assertFalse(VoiceScope.counts("chan", null, false, EMPTY));
    }

    @Test
    void includedChannelBeatsExcludedCategory() {
        GuildConfig c = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertTrue(VoiceScope.counts("chan", "cat", false, c));
    }

    @Test
    void excludedChannelBeatsIncludedCategory() {
        GuildConfig c = cfg(Map.of(
                VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, c));
    }

    @Test
    void exclusionWinsWhenTheSameIdIsInBothListsOfOneLevel() {
        GuildConfig channels = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CHANNELS, "chan",
                VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, "chan"));
        assertFalse(VoiceScope.counts("chan", null, true, channels));

        GuildConfig cats = cfg(Map.of(
                VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat",
                VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, cats));
    }

    @Test
    void includedCategoryLetsAPrivateChannelCount() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_INCLUDE_CATEGORIES, "cat"));
        assertTrue(VoiceScope.counts("chan", "cat", false, c));
    }

    @Test
    void excludedCategoryStopsAPublicChannel() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_EXCLUDE_CATEGORIES, "cat"));
        assertFalse(VoiceScope.counts("chan", "cat", true, c));
    }

    @Test
    void listsTolerateSpacesAndEmptySegments() {
        GuildConfig c = cfg(Map.of(VoiceTimeConfig.KEY_EXCLUDE_CHANNELS, " , chan , "));
        assertFalse(VoiceScope.counts("chan", null, true, c));
    }
}
