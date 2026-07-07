package dev.davimf.basebot.modules.base.snapshot;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.base.setup.InitialGuildSetup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the Discord snapshots fresh between periodic syncs: on join it registers + syncs the
 * guild, on leave it marks it absent, and on channel/role changes it re-syncs that guild
 * (debounced ~5s per guild so a bulk edit collapses into one resync).
 */
public final class GuildSnapshotListener extends ListenerAdapter {

    private static final long DEBOUNCE_SECONDS = 5;

    private final BotContext ctx;
    private final GuildSnapshotSync sync;
    private final Map<String, Future<?>> pending = new ConcurrentHashMap<>();

    public GuildSnapshotListener(BotContext ctx, GuildSnapshotSync sync) {
        this.ctx = ctx;
        this.sync = sync;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        scheduleResync(event.getGuild(), true);
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        ctx.scheduler().executor().execute(() -> sync.markAbsent(guildId));
    }

    @Override public void onChannelCreate(ChannelCreateEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onChannelDelete(ChannelDeleteEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onChannelUpdateName(ChannelUpdateNameEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onChannelUpdateParent(ChannelUpdateParentEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onRoleCreate(RoleCreateEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onRoleDelete(RoleDeleteEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onRoleUpdateName(RoleUpdateNameEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent e) { scheduleResync(e.getGuild(), false); }

    /** Coalesce a burst of events for one guild into a single resync. */
    private void scheduleResync(Guild guild, boolean runInitialSetup) {
        String guildId = guild.getId();
        Future<?> prev = pending.remove(guildId);
        if (prev != null) {
            prev.cancel(false);
        }
        Future<?> f = ctx.scheduler().executor().schedule(() -> {
            pending.remove(guildId);
            Guild g = ctx.jda() == null ? null : ctx.jda().getGuildById(guildId);
            if (g != null) {
                sync.syncGuild(g);
                if (runInitialSetup) InitialGuildSetup.runGuild(g, ctx);
            }
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        pending.put(guildId, f);
    }
}
