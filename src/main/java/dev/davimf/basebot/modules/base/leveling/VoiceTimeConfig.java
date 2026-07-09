package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.ConfigIds;

import java.util.Set;

/** Leitor puro do escopo de contagem de tempo em call (prefixo {@code voicetime:}). */
public final class VoiceTimeConfig {

    public static final String KEY_INCLUDE_CHANNELS = "voicetime:include-channels";
    public static final String KEY_EXCLUDE_CHANNELS = "voicetime:exclude-channels";
    public static final String KEY_INCLUDE_CATEGORIES = "voicetime:include-categories";
    public static final String KEY_EXCLUDE_CATEGORIES = "voicetime:exclude-categories";

    private VoiceTimeConfig() {}

    public static Set<String> includeChannels(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_INCLUDE_CHANNELS));
    }

    public static Set<String> excludeChannels(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_EXCLUDE_CHANNELS));
    }

    public static Set<String> includeCategories(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_INCLUDE_CATEGORIES));
    }

    public static Set<String> excludeCategories(GuildConfig cfg) {
        return ConfigIds.parse(cfg.setting(KEY_EXCLUDE_CATEGORIES));
    }
}
