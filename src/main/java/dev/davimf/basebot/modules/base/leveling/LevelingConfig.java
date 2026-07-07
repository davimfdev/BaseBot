package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
        String raw = cfg.setting(KEY_IGNORED);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
