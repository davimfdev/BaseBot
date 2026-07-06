package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.PermissionNames;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateParentEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTopicEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.EnumSet;

/** Logs de canais em log-canais: criado, deletado, atualizado (nome/categoria/tópico/perms).
 *  Threads são logadas em GuildLoggingListener (log-servidor), não aqui. */
public final class ChannelLoggingListener extends ListenerAdapter {

    private static final EnumSet<Permission> NONE = EnumSet.noneOf(Permission.class);

    private final BotContext ctx;

    public ChannelLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.getChannel().getType().isThread()) {
            return;
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## " + Emojis.of(Emojis.FOLDER, "📁") + " Canal criado\n---\n"
                        + "**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Tipo** · `" + event.getChannel().getType() + "`");
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.getChannel().getType().isThread()) {
            return;
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## " + Emojis.of(Emojis.TRASH, "🗑️") + " Canal deletado\n---\n"
                        + "**Nome** · `" + event.getChannel().getName()
                        + "`\n**Tipo** · `" + event.getChannel().getType() + "`");
    }

    @Override
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## " + Emojis.of(Emojis.EDIT, "✏️") + " Canal renomeado\n---\n"
                        + "**Antes** · `" + event.getOldValue() + "`\n**Depois** · `" + event.getNewValue() + "`");
    }

    @Override
    public void onChannelUpdateParent(ChannelUpdateParentEvent event) {
        String antes = event.getOldValue() == null ? "*nenhuma*" : event.getOldValue().getName();
        String depois = event.getNewValue() == null ? "*nenhuma*" : event.getNewValue().getName();
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## " + Emojis.of(Emojis.FOLDER_OPEN, "📂") + " Categoria alterada\n---\n"
                        + "**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Antes** · `" + antes + "`\n**Depois** · `" + depois + "`");
    }

    @Override
    public void onChannelUpdateTopic(ChannelUpdateTopicEvent event) {
        ChannelLog.post(ctx, event.getGuild().getId(), "log-canais",
                "## " + Emojis.of(Emojis.NOTE, "📝") + " Tópico alterado\n---\n"
                        + "**Canal** · " + event.getChannel().getAsMention()
                        + "\n**Depois** · " + (event.getNewValue() == null ? "*removido*" : event.getNewValue()));
    }

    @Override
    public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
        PermissionOverride po = event.getPermissionOverride();
        postOverride(event.getGuild().getId(), event.getChannel().getAsMention(), po,
                ActionType.CHANNEL_OVERRIDE_CREATE, "criadas",
                permDiff(NONE, NONE, po.getAllowed(), po.getDenied()));
    }

    @Override
    public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
        PermissionOverride po = event.getPermissionOverride();
        postOverride(event.getGuild().getId(), event.getChannel().getAsMention(), po,
                ActionType.CHANNEL_OVERRIDE_UPDATE, "alteradas",
                permDiff(event.getOldAllow(), event.getOldDeny(), po.getAllowed(), po.getDenied()));
    }

    @Override
    public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
        PermissionOverride po = event.getPermissionOverride();
        postOverride(event.getGuild().getId(), event.getChannel().getAsMention(), po,
                ActionType.CHANNEL_OVERRIDE_DELETE, "removidas",
                permDiff(po.getAllowed(), po.getDenied(), NONE, NONE));
    }

    private void postOverride(String guildId, String channelMention, PermissionOverride po,
                              ActionType type, String verb, String diff) {
        String alvo = overrideTarget(po);
        AuditLookup.lookup(ctx.jda() == null ? null : ctx.jda().getGuildById(guildId),
                channelMentionId(channelMention), type, actor ->
                        ChannelLog.post(ctx, guildId, "log-canais",
                                "## " + Emojis.of(Emojis.PERMS, "🔐") + " Permissões " + verb + "\n---\n"
                                        + "**Canal** · " + channelMention + "\n**Alvo** · " + alvo + diff
                                        + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }

    /** Builds the "Permitidas / Negadas / Neutras" lines from the old→new allow/deny sets. */
    private static String permDiff(EnumSet<Permission> oldAllow, EnumSet<Permission> oldDeny,
                                   EnumSet<Permission> newAllow, EnumSet<Permission> newDeny) {
        EnumSet<Permission> nowAllow = minus(newAllow, oldAllow);
        EnumSet<Permission> nowDeny = minus(newDeny, oldDeny);
        EnumSet<Permission> wereSet = EnumSet.noneOf(Permission.class);
        wereSet.addAll(oldAllow);
        wereSet.addAll(oldDeny);
        EnumSet<Permission> nowNeutral = EnumSet.noneOf(Permission.class);
        nowNeutral.addAll(wereSet);
        nowNeutral.removeAll(newAllow);
        nowNeutral.removeAll(newDeny);

        StringBuilder sb = new StringBuilder();
        if (!nowAllow.isEmpty()) {
            sb.append("\n").append(Emojis.of(Emojis.CHECK_YES, "✅")).append(" **Permitidas** · ").append(PermissionNames.names(nowAllow));
        }
        if (!nowDeny.isEmpty()) {
            sb.append("\n").append(Emojis.of(Emojis.CHECK_NO, "❌")).append(" **Negadas** · ").append(PermissionNames.names(nowDeny));
        }
        if (!nowNeutral.isEmpty()) {
            sb.append("\n").append(Emojis.of(Emojis.MINUS, "➖")).append(" **Neutras** · ").append(PermissionNames.names(nowNeutral));
        }
        if (sb.length() > 0) {
            sb.insert(0, "\n---");
        }
        return sb.toString();
    }

    private static EnumSet<Permission> minus(EnumSet<Permission> a, EnumSet<Permission> b) {
        EnumSet<Permission> r = EnumSet.noneOf(Permission.class);
        r.addAll(a);
        r.removeAll(b);
        return r;
    }

    private static String overrideTarget(PermissionOverride po) {
        if (po == null) {
            return "—";
        }
        if (po.getMember() != null) {
            return po.getMember().getAsMention();
        }
        if (po.getRole() != null) {
            return po.getRole().getAsMention();
        }
        return "—";
    }

    /** Audit override entries target the channel; extract its id from the mention "<#id>". */
    private static String channelMentionId(String mention) {
        return mention.replaceAll("[^0-9]", "");
    }
}
