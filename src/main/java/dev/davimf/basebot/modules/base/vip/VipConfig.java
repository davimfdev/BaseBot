package dev.davimf.basebot.modules.base.vip;

import dev.davimf.basebot.database.model.GuildConfig;

/** Config leve do VIP em GuildConfig (prefixo vip:). Getters puros. */
public final class VipConfig {
    public static final String KEY_ENABLED = "vip:enabled";
    public static final String KEY_MAX_BONUS = "vip:max-bonus-pct";
    public static final int DEFAULT_MAX_BONUS_PCT = 100;

    private VipConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }

    public static int maxBonusPct(GuildConfig cfg) {
        String raw = cfg.setting(KEY_MAX_BONUS);
        if (raw == null || raw.isBlank()) return DEFAULT_MAX_BONUS_PCT;
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException e) { return DEFAULT_MAX_BONUS_PCT; }
    }
}
