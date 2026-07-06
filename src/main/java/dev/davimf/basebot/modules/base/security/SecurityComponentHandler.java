package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Security buttons posted outside /setup (namespace "sec") — the anti-raid
 *  "Desativar lockdown" alert button and the verification "Verificar" panel button. */
public final class SecurityComponentHandler implements ComponentHandler {

    private final BotContext ctx;

    public SecurityComponentHandler(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String namespace() {
        return AntiRaidService.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "raidunlock" -> raidUnlock(event, ctx);
            case "verify" -> verify(event, ctx);
            default -> { /* not ours */ }
        }
    }

    private void raidUnlock(ButtonInteractionEvent event, BotContext ctx) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            Replies.ephemeral(event, ctx, "Apenas quem tem **Gerenciar Servidor** pode desativar o lockdown.");
            return;
        }
        AntiRaidService.unlock(ctx, event.getGuild());
        Replies.reply(event, ctx, "Lockdown desativado.");
    }

    private void verify(ButtonInteractionEvent event, BotContext ctx) {
        var guild = event.getGuild();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (!SecurityConfig.verify(cfg)) {
            Replies.ephemeral(event, ctx, "A verificação não está ativa.");
            return;
        }
        String memberRoleId = cfg.role("membro");
        Role memberRole = memberRoleId == null ? null : guild.getRoleById(memberRoleId);
        if (memberRole == null) {
            Replies.ephemeral(event, ctx, "Cargo de **membro** não configurado (/setup → Cargos).");
            return;
        }
        if (!guild.getSelfMember().canInteract(memberRole)) {
            Replies.ephemeral(event, ctx, "Não consigo atribuir o cargo de membro (acima do meu cargo).");
            return;
        }
        Member m = event.getMember();
        guild.addRoleToMember(m, memberRole).reason("Verificação concluída").queue(ok -> {}, err -> {});
        String unvId = cfg.role("nao-verificado");
        Role unv = unvId == null ? null : guild.getRoleById(unvId);
        if (unv != null && m.getRoles().contains(unv) && guild.getSelfMember().canInteract(unv)) {
            guild.removeRoleFromMember(m, unv).reason("Verificação concluída").queue(ok -> {}, err -> {});
        }
        Replies.ephemeral(event, ctx, Emojis.of(Emojis.CHECK_YES, "✅") + " Verificado! Bem-vindo.");
    }
}
