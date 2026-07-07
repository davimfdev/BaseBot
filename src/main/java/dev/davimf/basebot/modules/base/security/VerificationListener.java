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
        var guild = event.getGuild();
        // Já verificado neste servidor (e não expulso desde então): concede o cargo direto.
        if (ctx.database().verification().isVerified(guild.getId(), event.getUser().getId())) {
            String memberId = cfg.role("membro");
            Role member = memberId == null ? null : guild.getRoleById(memberId);
            if (member != null && guild.getSelfMember().canInteract(member)) {
                guild.addRoleToMember(event.getMember(), member)
                        .reason("Verificação: auto (re-entrada)").queue(ok -> {}, err -> {});
            }
            return; // não aplica o gate
        }
        String roleId = cfg.role("nao-verificado");
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role != null && guild.getSelfMember().canInteract(role)) {
            guild.addRoleToMember(event.getMember(), role)
                    .reason("Verificação: aguardando").queue(ok -> {}, err -> {});
        }
    }
}
