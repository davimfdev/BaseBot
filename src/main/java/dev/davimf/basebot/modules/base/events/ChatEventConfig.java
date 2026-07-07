package dev.davimf.basebot.modules.base.events;

import dev.davimf.basebot.database.model.GuildConfig;

/** Leitor puro da config de eventos de chat (prefixo {@code event:}). */
public final class ChatEventConfig {

    public static final String KEY_ENABLED = "event:enabled";
    public static final String KEY_CHANNEL = "event-channel";
    public static final String KEY_MIN = "event:min-interval";
    public static final String KEY_MAX = "event:max-interval";

    private ChatEventConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    public static String channelId(GuildConfig cfg) { return cfg.channel(KEY_CHANNEL); }

    public static long minMinutes(GuildConfig cfg) { return Math.max(1, longOr(cfg.setting(KEY_MIN), 30)); }

    public static long maxMinutes(GuildConfig cfg) { return Math.max(minMinutes(cfg), longOr(cfg.setting(KEY_MAX), 120)); }

    private static long longOr(String v, long def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
