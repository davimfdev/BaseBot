package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Anti-nuke: conta ações destrutivas por ator (resolvido no audit log com retry) e remove
 *  os cargos do ator ao passar do limite. Janela em memória; estado perdido no restart (ok). */
public final class AntiNukeListener extends ListenerAdapter {

    private final BotContext ctx;
    private volatile ActorWindow window = new ActorWindow(60_000);
    private volatile long windowStamp = Long.MIN_VALUE;
    private final Set<String> neutralized = ConcurrentHashMap.newKeySet();

    public AntiNukeListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.isFromGuild()) {
            onDestructive(event.getGuild(), event.getChannel().getId(), ActionType.CHANNEL_DELETE);
        }
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        onDestructive(event.getGuild(), event.getRole().getId(), ActionType.ROLE_DELETE);
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        onDestructive(event.getGuild(), event.getUser().getId(), ActionType.BAN);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        // Só conta se o audit log confirmar um KICK desse alvo (saídas voluntárias não batem).
        onDestructive(event.getGuild(), event.getUser().getId(), ActionType.KICK);
    }

    private void onDestructive(Guild guild, String targetId, ActionType type) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.antinuke(cfg)) {
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            return; // degrada em silêncio
        }
        ensureWindow(cfg);
        NukeAuditLookup.resolveActor(guild, targetId, type, ctx, user -> {
            if (user.isBot() || user.getIdLong() == guild.getJDA().getSelfUser().getIdLong()
                    || user.getIdLong() == guild.getOwnerIdLong()) {
                return;
            }
            String key = guild.getId() + ":" + user.getId();
            guild.retrieveMember(user).queue(member -> {
                List<String> roleIds = member.getRoles().stream().map(ISnowflake::getId).toList();
                if (SecurityConfig.isNukeWhitelisted(cfg, user.getId(), roleIds)) {
                    return;
                }
                int count = window.record(key, System.currentTimeMillis());
                if (count >= SecurityConfig.antinukeMax(cfg) && neutralized.add(key)) {
                    AntiNukeService.neutralize(ctx, guild, member, count);
                }
            }, err -> { });
        });
    }

    private void ensureWindow(GuildConfig cfg) {
        long stamp = SecurityConfig.antinukeWindowSeconds(cfg);
        if (stamp != windowStamp) {
            window = new ActorWindow(stamp * 1000L);
            windowStamp = stamp;
        }
    }
}
