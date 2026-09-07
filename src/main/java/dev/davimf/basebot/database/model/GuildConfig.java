package dev.davimf.basebot.database.model;

import java.util.List;
import java.util.Map;

/**
 * Per-guild configuration — the "source of truth" stored in Postgres and edited by
 * the web dashboard. Everything here is Guild-scoped per the Golden Rule; only the
 * global bot profile (/bot-name, /bot-icon) lives outside this.
 *
 * <p>This is intentionally a flexible carrier: well-known fields are typed, and a
 * free-form {@code toggles}/{@code channels}/{@code roles} map absorbs the long tail
 * of dashboard settings so the schema can evolve (incl. the Supabase -&gt; Neon move)
 * without breaking the bot.
 */
public record GuildConfig(
        String guildId,
        // Common log channel IDs (nullable when unset).
        String logChannelId,
        String ticketLogChannelId,
        // Generic, dashboard-driven settings.
        Map<String, String> channels,   // logical name -> channel id
        Map<String, String> roles,      // logical name -> role id
        Map<String, Boolean> toggles,   // feature flag -> on/off
        List<String> staffRoleIds,      // ticket-allowed staff roles
        Map<String, String> settings    // free-form text config (ticket desc/emoji, percentages)
) {

    public static GuildConfig empty(String guildId) {
        return new GuildConfig(guildId, null, null, Map.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    public boolean toggle(String key, boolean def) {
        return toggles.getOrDefault(key, def);
    }

    public String channel(String key) {
        return channels.get(key);
    }

    public String role(String key) {
        return roles.get(key);
    }

    public String setting(String key) {
        return settings.get(key);
    }
}
