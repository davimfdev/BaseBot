package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Logs de banimento/desbanimento em log-bans, com moderador+motivo do audit. */
public final class BanLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public BanLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        ctx.database().actionLogs().log(event.getGuild().getId(), null, event.getUser().getId(), "BAN", null);
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.BAN, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-bans",
                        "## " + Emojis.of(Emojis.BAN, "🔨") + " Banido\n---\n"
                                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getUser().getAsTag()
                                + " · `" + event.getUser().getId() + "`\n---\n"
                                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()
                                + "\n" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + actor.reason()));
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        AuditLookup.lookup(event.getGuild(), event.getUser().getId(), ActionType.UNBAN, actor ->
                ChannelLog.post(ctx, event.getGuild().getId(), "log-bans",
                        "## " + Emojis.of(Emojis.RECYCLE, "♻️") + " Desbanido\n---\n"
                                + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + event.getUser().getAsTag()
                                + " · `" + event.getUser().getId() + "`\n---\n"
                                + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · " + actor.moderatorMention()));
    }
}
