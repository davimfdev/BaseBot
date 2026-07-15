package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;

/** Leitor puro da config de economia (prefixo {@code eco:}). */
public final class EconomyConfig {

    public static final String KEY_ENABLED = "eco:enabled";
    public static final String KEY_CURRENCY_NAME = "eco:currency-name";
    public static final String KEY_CURRENCY_EMOJI = "eco:currency-emoji";
    public static final String KEY_DAILY = "eco:daily";
    public static final String KEY_WORK_MIN = "eco:work-min";
    public static final String KEY_WORK_MAX = "eco:work-max";
    public static final String KEY_WORK_COOLDOWN = "eco:work-cooldown";

    private EconomyConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    public static String currencyName(GuildConfig cfg) {
        String v = cfg.setting(KEY_CURRENCY_NAME);
        return v == null || v.isBlank() ? "moedas" : v.trim();
    }

    public static String currencyEmoji(GuildConfig cfg) {
        String v = cfg.setting(KEY_CURRENCY_EMOJI);
        return v == null || v.isBlank() ? Emojis.of(Emojis.MONEY, "🪙") : v.trim();
    }

    /** Emoji da moeda para contextos que NÃO renderizam emoji custom (ex.: descrições de
     *  opções de select menu, onde {@code <:nome:id>} apareceria como texto cru). Usa o
     *  fallback Unicode em vez do emoji custom da aplicação. */
    public static String currencyEmojiPlain(GuildConfig cfg) {
        String v = cfg.setting(KEY_CURRENCY_EMOJI);
        return v == null || v.isBlank() ? "🪙" : v.trim();
    }

    public static long daily(GuildConfig cfg) { return longOr(cfg.setting(KEY_DAILY), 500); }
    public static long workMin(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_MIN), 50); }
    public static long workMax(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_MAX), 250); }
    public static long workCooldownSeconds(GuildConfig cfg) { return longOr(cfg.setting(KEY_WORK_COOLDOWN), 3600); }

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
