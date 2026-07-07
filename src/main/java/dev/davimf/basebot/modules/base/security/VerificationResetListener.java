package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Esquece a verificação quando a pessoa é EXPULSA (kick, confirmado no audit log) ou
 *  banida. Saída voluntária mantém a verificação (auto-cargo na re-entrada). */
public final class VerificationResetListener extends ListenerAdapter {

    private final BotContext ctx;

    public VerificationResetListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.verify(cfg)) {
            return;
        }
        if (!event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            return; // sem audit log não dá pra distinguir kick de saída voluntária
        }
        String userId = event.getUser().getId();
        // resolveActor só chama de volta se houver um KICK recente desse alvo no audit log.
        NukeAuditLookup.resolveActor(event.getGuild(), userId, ActionType.KICK, ctx,
                actor -> ctx.database().verification().forget(event.getGuild().getId(), userId));
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        ctx.database().verification().forget(event.getGuild().getId(), event.getUser().getId());
    }
}
