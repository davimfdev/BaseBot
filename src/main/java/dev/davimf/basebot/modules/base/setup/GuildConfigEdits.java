package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, immutable transformations of {@link GuildConfig}. Each method returns a new
 * config with one field changed, copying maps so callers (and the dashboard's stored
 * row) are never mutated in place. Used by {@code /setup} between load and save.
 */
public final class GuildConfigEdits {

    private GuildConfigEdits() {}

    public static GuildConfig withLogChannel(GuildConfig c, String channelId) {
        return new GuildConfig(c.guildId(), channelId, c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds(), c.settings());
    }

    public static GuildConfig withTicketLogChannel(GuildConfig c, String channelId) {
        return new GuildConfig(c.guildId(), c.logChannelId(), channelId,
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds(), c.settings());
    }

    public static GuildConfig withChannel(GuildConfig c, String key, String channelId) {
        Map<String, String> m = new HashMap<>(c.channels());
        m.put(key, channelId);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                Map.copyOf(m), c.roles(), c.toggles(), c.staffRoleIds(), c.settings());
    }

    public static GuildConfig withRole(GuildConfig c, String key, String roleId) {
        Map<String, String> m = new HashMap<>(c.roles());
        m.put(key, roleId);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), Map.copyOf(m), c.toggles(), c.staffRoleIds(), c.settings());
    }

    public static GuildConfig withToggle(GuildConfig c, String key, boolean value) {
        Map<String, Boolean> m = new HashMap<>(c.toggles());
        m.put(key, value);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), Map.copyOf(m), c.staffRoleIds(), c.settings());
    }

    public static GuildConfig withStaffRoles(GuildConfig c, List<String> roleIds) {
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), List.copyOf(roleIds), c.settings());
    }

    public static GuildConfig withSetting(GuildConfig c, String key, String value) {
        Map<String, String> m = new HashMap<>(c.settings());
        m.put(key, value);
        return new GuildConfig(c.guildId(), c.logChannelId(), c.ticketLogChannelId(),
                c.channels(), c.roles(), c.toggles(), c.staffRoleIds(), Map.copyOf(m));
    }
}
