package dev.davimf.basebot.modules.base.welcome;

import dev.davimf.basebot.database.model.GuildConfig;

/** Leitor puro da config de boas-vindas/despedida (prefixo {@code welcome:}). */
public final class WelcomeConfig {

    public static final String KEY_ENABLED = "welcome:enabled";
    public static final String KEY_CHANNEL = "welcome:channel";
    public static final String KEY_DM = "welcome:dm";
    public static final String KEY_MESSAGE = "welcome:message";
    public static final String KEY_IMAGE = "welcome:image";
    public static final String KEY_AUTOROLE = "welcome:autorole";
    public static final String KEY_FAREWELL_ENABLED = "welcome:farewell-enabled";
    public static final String KEY_FAREWELL_CHANNEL = "welcome:farewell-channel";
    public static final String KEY_FAREWELL_MESSAGE = "welcome:farewell-message";

    /** Slot de cargo de fallback do autorole quando o módulo facs está em uso. */
    public static final String FALLBACK_AUTOROLE_KEY = "sem-set";

    public static final String DEFAULT_WELCOME =
            "Bem-vindo(a) ao **{server}**, {mention}! Você é o membro **{count}**.";
    public static final String DEFAULT_FAREWELL =
            "**{user}** saiu do servidor. Agora somos **{count}**.";

    private WelcomeConfig() {}

    public static boolean enabled(GuildConfig cfg) { return cfg.toggle(KEY_ENABLED, false); }
    public static boolean dm(GuildConfig cfg) { return cfg.toggle(KEY_DM, false); }
    public static boolean farewellEnabled(GuildConfig cfg) { return cfg.toggle(KEY_FAREWELL_ENABLED, false); }

    public static String message(GuildConfig cfg) { return orDefault(cfg.setting(KEY_MESSAGE), DEFAULT_WELCOME); }
    public static String farewellMessage(GuildConfig cfg) { return orDefault(cfg.setting(KEY_FAREWELL_MESSAGE), DEFAULT_FAREWELL); }

    /** {@code [vaultChannelId, vaultMessageId]} ou {@code null} quando não há imagem. */
    public static String[] imageRef(GuildConfig cfg) {
        String raw = cfg.setting(KEY_IMAGE);
        if (raw == null || raw.isBlank() || !raw.contains(":")) {
            return null;
        }
        String[] parts = raw.split(":", 2);
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
