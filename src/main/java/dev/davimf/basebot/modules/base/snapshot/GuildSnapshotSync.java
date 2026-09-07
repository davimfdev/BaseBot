package dev.davimf.basebot.modules.base.snapshot;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.SnapshotRepository;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes each guild's channel/role snapshot to Postgres so the dashboard can validate
 * channels/roles without a bot token. Full-sync on boot + periodically; incremental via the
 * listener. Skips entirely when no BOT_INSTANCE_ID is configured.
 */
public final class GuildSnapshotSync {

    private static final Logger log = LoggerFactory.getLogger(GuildSnapshotSync.class);

    private final BotContext ctx;
    private final SnapshotRepository repo;
    private final String clientName;

    public GuildSnapshotSync(BotContext ctx) {
        this.ctx = ctx;
        this.repo = new SnapshotRepository(ctx.database().postgres(), resolveInstanceId(ctx));
        this.clientName = ctx.config().instance().clientName();
    }

    /** Env override ({@code BOT_INSTANCE_ID}) when set; otherwise the id generated + persisted locally on first boot. */
    private static String resolveInstanceId(BotContext ctx) {
        String configured = ctx.config().instance().instanceId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return new dev.davimf.basebot.database.sqlite.LocalInstanceRepository(ctx.database().sqlite())
                .getOrCreate();
    }

    /** Um cargo é atribuível pelo bot se ele tem Gerenciar Cargos, está acima na hierarquia e o cargo não é gerenciado. */
    public static boolean botCanAssign(boolean hasManageRoles, boolean canInteract, boolean managed) {
        return hasManageRoles && canInteract && !managed;
    }

    /** Registra/atualiza esta instância no boot (precisa de JDA vivo). */
    public void bootstrapInstance() {
        if (ctx.jda() == null) {
            return;
        }
        String botUserId = ctx.jda().getSelfUser().getId();
        repo.upsertInstance(botUserId, null, clientName);
    }

    public void syncAll() {
        syncAll("periodic");
    }

    public void syncAll(String trigger) {
        if (ctx.jda() == null) {
            return;
        }
        for (Guild g : ctx.jda().getGuilds()) {
            try {
                syncGuild(g, trigger);
            } catch (Exception e) {
                log.warn("snapshot sync falhou para guild {}", g.getId(), e);
            }
        }
    }

    public void syncGuild(Guild g) {
        syncGuild(g, "event");
    }

    /**
     * Full diff-based reconcile of a guild's channels + roles. Cheap when nothing changed: the
     * repository upserts only rows whose data differs and deletes only rows that vanished, so a
     * no-change sync writes zero rows. Logs one structured line with the write/delete counts.
     */
    public void syncGuild(Guild g, String trigger) {
        long started = System.currentTimeMillis();
        // bot_guilds primeiro: estabelece/atualiza o vínculo guild->instância (guard interno no repo).
        repo.upsertGuild(g.getId(), g.getName(), g.getOwnerId());
        Member self = g.getSelfMember();
        boolean manageRoles = self.hasPermission(Permission.MANAGE_ROLES);

        List<SnapshotRepository.ChannelRow> channels = new ArrayList<>();
        for (GuildChannel ch : g.getChannels()) {
            channels.add(channelRow(self, ch));
        }
        SnapshotRepository.SyncCounts chCounts = repo.reconcileChannels(g.getId(), channels);

        List<SnapshotRepository.RoleRow> roles = new ArrayList<>();
        for (Role r : g.getRoles()) {
            roles.add(roleRow(self, manageRoles, r));
        }
        SnapshotRepository.SyncCounts roleCounts = repo.reconcileRoles(g.getId(), roles);

        if (chCounts.written() + chCounts.deleted() + roleCounts.written() + roleCounts.deleted() > 0) {
            log.info("snapshot sync guild={} trigger={} channels(total={} written={} deleted={} unchanged={}) "
                            + "roles(total={} written={} deleted={} unchanged={}) duration_ms={}",
                    g.getId(), trigger,
                    chCounts.total(), chCounts.written(), chCounts.deleted(), chCounts.unchanged(),
                    roleCounts.total(), roleCounts.written(), roleCounts.deleted(), roleCounts.unchanged(),
                    System.currentTimeMillis() - started);
        } else {
            log.debug("snapshot sync guild={} trigger={} no-op (nothing changed) duration_ms={}",
                    g.getId(), trigger, System.currentTimeMillis() - started);
        }
    }

    /** Incremental: upsert just the one channel that changed (rename/move/create/perms). */
    public void syncChannel(Guild g, GuildChannel ch) {
        repo.upsertChannel(g.getId(), channelRow(g.getSelfMember(), ch));
    }

    /** Incremental: drop the one channel that was deleted. */
    public void removeChannel(String guildId, String channelId) {
        repo.deleteChannel(guildId, channelId);
    }

    /** Incremental: upsert just the one role that changed (rename/create). */
    public void syncRole(Guild g, Role r) {
        Member self = g.getSelfMember();
        repo.upsertRole(g.getId(), roleRow(self, self.hasPermission(Permission.MANAGE_ROLES), r));
    }

    /** Incremental: drop the one role that was deleted. */
    public void removeRole(String guildId, String roleId) {
        repo.deleteRole(guildId, roleId);
    }

    private static SnapshotRepository.ChannelRow channelRow(Member self, GuildChannel ch) {
        boolean canView = self.hasPermission(ch, Permission.VIEW_CHANNEL);
        boolean canSend = ch instanceof GuildMessageChannel
                && self.hasPermission(ch, Permission.MESSAGE_SEND);
        String parentId = (ch instanceof ICategorizableChannel cat && cat.getParentCategory() != null)
                ? cat.getParentCategory().getId() : null;
        Integer position = (ch instanceof IPositionableChannel pc) ? pc.getPositionRaw() : null;
        return new SnapshotRepository.ChannelRow(
                ch.getId(), ch.getName(), ch.getType().name(), parentId, position, canView, canSend);
    }

    private static SnapshotRepository.RoleRow roleRow(Member self, boolean manageRoles, Role r) {
        boolean canAssign = botCanAssign(manageRoles, self.canInteract(r), r.isManaged());
        return new SnapshotRepository.RoleRow(
                r.getId(), r.getName(), r.getPositionRaw(), r.isManaged(), canAssign);
    }

    public void markAbsent(String guildId) {
        repo.markAbsent(guildId);
    }
}
