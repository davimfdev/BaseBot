package dev.davimf.basebot.modules.base.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks per-guild invite use counts so a member join can be attributed to the invite whose
 * counter incremented. The cache is primed on guild-ready and kept fresh on invite
 * create/delete. Requires {@code MANAGE_SERVER} (to read invites) + the {@code GUILD_INVITES}
 * intent; degrades gracefully (resolves to {@code null}) for vanity URLs or missing perms.
 */
public final class InviteTracker extends ListenerAdapter {

    /** guildId -> (invite code -> uses) */
    private final Map<String, Map<String, Integer>> cache = new ConcurrentHashMap<>();

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        prime(event.getGuild());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        prime(event.getGuild());
    }

    private void prime(Guild guild) {
        guild.retrieveInvites().queue(invites -> {
            Map<String, Integer> m = new ConcurrentHashMap<>();
            for (Invite i : invites) {
                m.put(i.getCode(), i.getUses());
            }
            cache.put(guild.getId(), m);
        }, err -> { });
    }

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        cache.computeIfAbsent(event.getGuild().getId(), k -> new ConcurrentHashMap<>())
                .put(event.getCode(), 0);
    }

    @Override
    public void onGuildInviteDelete(GuildInviteDeleteEvent event) {
        Map<String, Integer> m = cache.get(event.getGuild().getId());
        if (m != null) {
            m.remove(event.getCode());
        }
    }

    /**
     * Retrieves the current invites, finds the one whose {@code uses} increased versus the
     * cache, refreshes the cache, and passes that invite to {@code cb} ({@code null} if it
     * cannot be determined). Always invokes {@code cb} exactly once.
     */
    public void resolveUsedInvite(Guild guild, Consumer<Invite> cb) {
        guild.retrieveInvites().queue(invites -> {
            Map<String, Integer> prev = cache.getOrDefault(guild.getId(), Map.of());
            Invite used = null;
            Map<String, Integer> fresh = new ConcurrentHashMap<>();
            for (Invite i : invites) {
                fresh.put(i.getCode(), i.getUses());
                int before = prev.getOrDefault(i.getCode(), 0);
                if (used == null && i.getUses() > before) {
                    used = i;
                }
            }
            cache.put(guild.getId(), fresh);
            cb.accept(used);
        }, err -> cb.accept(null));
    }
}
