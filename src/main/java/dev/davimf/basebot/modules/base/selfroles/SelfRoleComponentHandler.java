package dev.davimf.basebot.modules.base.selfroles;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.List;
import java.util.Optional;

/** Runtime dos painéis de self-role (namespace "selfrole"): alterna cargos no clique. */
public final class SelfRoleComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SelfRoleView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"toggle".equals(id.action())) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Não disponível fora de um servidor.");
            return;
        }
        Guild guild = event.getGuild();
        Role role = guild.getRoleById(id.arg(1));
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo não encontrado.");
            return;
        }
        if (!guild.getSelfMember().canInteract(role)) {
            Replies.ephemeral(event, ctx, "Não consigo gerenciar esse cargo (acima do meu).");
            return;
        }
        Member m = event.getMember();
        if (m.getRoles().contains(role)) {
            guild.removeRoleFromMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            Replies.ephemeral(event, ctx, "Cargo " + role.getAsMention() + " removido.");
            return;
        }
        SelfRolePanel panel = repo(ctx).find(id.arg(0)).orElse(null);
        if (panel != null && panel.unique()) {
            for (String otherId : panel.roleIds()) {
                Role other = guild.getRoleById(otherId);
                if (other != null && !other.equals(role) && m.getRoles().contains(other)
                        && guild.getSelfMember().canInteract(other)) {
                    guild.removeRoleFromMember(m, other).reason("Self-role exclusivo").queue(ok -> {}, err -> {});
                }
            }
        }
        guild.addRoleToMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
        Replies.ephemeral(event, ctx, "Cargo " + role.getAsMention() + " adicionado.");
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"select".equals(id.action())) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Não disponível fora de um servidor.");
            return;
        }
        Optional<SelfRolePanel> found = repo(ctx).find(id.arg(0));
        if (found.isEmpty()) {
            Replies.ephemeral(event, ctx, "Painel não encontrado.");
            return;
        }
        Guild guild = event.getGuild();
        Member m = event.getMember();
        List<String> selected = event.getValues();
        for (String roleId : found.get().roleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role == null || !guild.getSelfMember().canInteract(role)) {
                continue;
            }
            boolean want = selected.contains(roleId);
            boolean has = m.getRoles().contains(role);
            if (want && !has) {
                guild.addRoleToMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            } else if (!want && has) {
                guild.removeRoleFromMember(m, role).reason("Self-role").queue(ok -> {}, err -> {});
            }
        }
        Replies.ephemeral(event, ctx, "Cargos atualizados.");
    }

    private static SelfRolePanelRepository repo(BotContext ctx) {
        return new SelfRolePanelRepository(ctx.database().postgres());
    }
}
