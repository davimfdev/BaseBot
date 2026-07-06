package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Ao entrar, atribui o cargo "nao-verificado" (gate). O membro vira "membro" ao verificar. */
public final class VerificationListener extends ListenerAdapter {

    private final BotContext ctx;

    public VerificationListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!SecurityConfig.verify(cfg)) {
            return;
        }
        String roleId = cfg.role("nao-verificado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role != null && event.getGuild().getSelfMember().canInteract(role)) {
            event.getGuild().addRoleToMember(event.getMember(), role)
                    .reason("Verificação: aguardando").queue(ok -> {}, err -> {});
        }
    }
}
