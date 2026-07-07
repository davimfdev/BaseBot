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
        if (ctx.jda() == null) {
            return;
        }
        for (Guild g : ctx.jda().getGuilds()) {
            try {
                syncGuild(g);
            } catch (Exception e) {
                log.warn("snapshot sync falhou para guild {}", g.getId(), e);
            }
        }
    }

    public void syncGuild(Guild g) {
        // bot_guilds primeiro: estabelece/atualiza o vínculo guild->instância (guard interno no repo).
        repo.upsertGuild(g.getId(), g.getName(), g.getOwnerId());
        Member self = g.getSelfMember();
        boolean manageRoles = self.hasPermission(Permission.MANAGE_ROLES);

        List<SnapshotRepository.ChannelRow> channels = new ArrayList<>();
        for (GuildChannel ch : g.getChannels()) {
            boolean canView = self.hasPermission(ch, Permission.VIEW_CHANNEL);
            boolean canSend = ch instanceof GuildMessageChannel
                    && self.hasPermission(ch, Permission.MESSAGE_SEND);
            String parentId = (ch instanceof ICategorizableChannel cat && cat.getParentCategory() != null)
                    ? cat.getParentCategory().getId() : null;
            Integer position = (ch instanceof IPositionableChannel pc) ? pc.getPositionRaw() : null;
            channels.add(new SnapshotRepository.ChannelRow(
                    ch.getId(), ch.getName(), ch.getType().name(), parentId,
                    position, canView, canSend));
        }
        repo.replaceChannels(g.getId(), channels);

        List<SnapshotRepository.RoleRow> roles = new ArrayList<>();
        for (Role r : g.getRoles()) {
            boolean canAssign = botCanAssign(manageRoles, self.canInteract(r), r.isManaged());
            roles.add(new SnapshotRepository.RoleRow(
                    r.getId(), r.getName(), r.getPositionRaw(), r.isManaged(), canAssign));
        }
        repo.replaceRoles(g.getId(), roles);
    }

    public void markAbsent(String guildId) {
        repo.markAbsent(guildId);
    }
}
