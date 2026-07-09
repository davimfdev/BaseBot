package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Set;

/** Leitor puro da config de leveling (prefixo {@code level:}). */
public final class LevelingConfig {

    public static final String KEY_ENABLED = "level:enabled";
    public static final String KEY_NOTIFY = "level:notify";
    public static final String KEY_IGNORED = "level:ignored-channels";
    /** Chave em guild_config.channels do canal fixo de level-up. */
    public static final String KEY_NOTIFY_CHANNEL = "level-notify";

    private LevelingConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    /** {@code current} (default), {@code channel}, {@code dm} ou {@code off}. */
    public static String notifyMode(GuildConfig cfg) {
        String v = cfg.setting(KEY_NOTIFY);
        return v == null || v.isBlank() ? "current" : v.trim();
    }

    public static String notifyChannelId(GuildConfig cfg) { return cfg.channel(KEY_NOTIFY_CHANNEL); }

    public static Set<String> ignoredChannels(GuildConfig cfg) {
        return dev.davimf.basebot.util.ConfigIds.parse(cfg.setting(KEY_IGNORED));
    }
}
