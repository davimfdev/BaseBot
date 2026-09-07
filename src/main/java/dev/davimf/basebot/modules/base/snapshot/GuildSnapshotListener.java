package dev.davimf.basebot.modules.base.snapshot;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.base.setup.InitialGuildSetup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdatePositionEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.override.GenericPermissionOverrideEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the Discord snapshots fresh between periodic reconciles, incrementally. Narrow changes
 * (a channel/role created, renamed, re-parented or deleted) touch <b>only</b> that one row via a
 * single upsert/delete. Changes that cascade across many rows — position moves, permission edits,
 * or the bot's own roles changing (which flip {@code bot_can_view/send/assign} in bulk) — trigger a
 * debounced full diff-based reconcile of the guild (~5s coalescing so a bulk edit collapses into
 * one pass, and the reconcile still writes only the rows that actually differ).
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

    // --- Narrow: single channel/role changed -> one upsert/delete, no full reconcile. ---

    @Override public void onChannelCreate(ChannelCreateEvent e) { upsertChannel(e); }
    @Override public void onChannelUpdateName(ChannelUpdateNameEvent e) { upsertChannel(e); }
    @Override public void onChannelUpdateParent(ChannelUpdateParentEvent e) { upsertChannel(e); }

    @Override
    public void onChannelDelete(ChannelDeleteEvent e) {
        if (!(e.getChannel() instanceof GuildChannel gc)) return;
        String guildId = e.getGuild().getId();
        String channelId = gc.getId();
        ctx.scheduler().executor().execute(() -> sync.removeChannel(guildId, channelId));
    }

    @Override public void onRoleCreate(RoleCreateEvent e) { upsertRole(e.getGuild(), e.getRole()); }
    @Override public void onRoleUpdateName(RoleUpdateNameEvent e) { upsertRole(e.getGuild(), e.getRole()); }

    @Override
    public void onRoleDelete(RoleDeleteEvent e) {
        String guildId = e.getGuild().getId();
        String roleId = e.getRole().getId();
        ctx.scheduler().executor().execute(() -> sync.removeRole(guildId, roleId));
    }

    // A permission override is scoped to exactly one channel: recompute just that channel's
    // bot_can_view / bot_can_send.
    @Override public void onPermissionOverrideCreate(PermissionOverrideCreateEvent e) { overrideChannel(e); }
    @Override public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent e) { overrideChannel(e); }
    @Override public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent e) { overrideChannel(e); }

    // --- Broad: change cascades across many rows -> debounced full reconcile of the guild. ---

    // A position drag re-numbers a whole run of siblings, so coalesce into one reconcile.
    @Override public void onChannelUpdatePosition(ChannelUpdatePositionEvent e) { scheduleResync(e.getGuild(), false); }
    @Override public void onRoleUpdatePosition(RoleUpdatePositionEvent e) { scheduleResync(e.getGuild(), false); }
    // If the bot's own role gains/loses Manage Roles, bot_can_assign flips for every role and
    // bot_can_view/send for every channel — reconcile the whole guild.
    @Override public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent e) { scheduleResync(e.getGuild(), false); }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent e) {
        if (isSelf(e.getGuild(), e.getMember())) scheduleResync(e.getGuild(), false);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent e) {
        if (isSelf(e.getGuild(), e.getMember())) scheduleResync(e.getGuild(), false);
    }

    private void upsertChannel(GenericChannelEvent e) {
        if (!(e.getChannel() instanceof GuildChannel gc)) return;
        Guild g = e.getGuild();
        ctx.scheduler().executor().execute(() -> sync.syncChannel(g, gc));
    }

    private void upsertRole(Guild g, net.dv8tion.jda.api.entities.Role r) {
        ctx.scheduler().executor().execute(() -> sync.syncRole(g, r));
    }

    private void overrideChannel(GenericPermissionOverrideEvent e) {
        if (!(e.getChannel() instanceof GuildChannel gc)) return;
        Guild g = e.getGuild();
        ctx.scheduler().executor().execute(() -> sync.syncChannel(g, gc));
    }

    private static boolean isSelf(Guild g, Member member) {
        return member != null && member.getId().equals(g.getSelfMember().getId());
    }

    /** Coalesce a burst of cascading events for one guild into a single full reconcile. */
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
                sync.syncGuild(g, runInitialSetup ? "guild-join" : "event");
                if (runInitialSetup) InitialGuildSetup.runGuild(g, ctx);
            }
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        pending.put(guildId, f);
    }
}
