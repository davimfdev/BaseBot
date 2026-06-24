package dev.davimf.basebot.modules.facs.sets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/** Approve/reject handler for {@code /solicitar-cargo} requests (BOTSPECS Module 4). */
public final class SetRequestComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return SetRequestView.NS;
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        String requesterId = id.arg(0);
        String roleId = id.arg(1);
        Role role = event.getGuild().getRoleById(roleId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));

        if ("reject".equals(id.action())) {
            event.editComponents(SetRequestView.resolved(accent, requesterId, roleId,
                    "❌ Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    requesterId, "SET_REJECT", roleId);
            return;
        }
        if (!"approve".equals(id.action())) {
            return;
        }
        if (role == null) {
            event.reply("Esse cargo não existe mais.").setEphemeral(true).queue();
            return;
        }
        Member approver = event.getMember();
        if (approver.getId().equals(requesterId)) {
            event.reply("Você não pode aprovar a sua própria solicitação.").setEphemeral(true).queue();
            return;
        }
        if (!Moderation.canManageRole(approver, role, event.getGuild().getSelfMember())) {
            event.reply("Você precisa estar acima desse cargo na hierarquia para aprovar.")
                    .setEphemeral(true).queue();
            return;
        }
        event.getGuild().retrieveMemberById(requesterId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Set aprovado por "
                        + approver.getUser().getName()).queue(ok -> {
                    event.editComponents(SetRequestView.resolved(accent, requesterId, roleId,
                            "✅ Aprovado por <@" + approver.getId() + ">.")).useComponentsV2().queue();
                    ctx.database().actionLogs().log(event.getGuild().getId(), approver.getId(),
                            requesterId, "SET_APPROVE", roleId);
                    FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                            "## 🎖️ Set aprovado\n<@" + requesterId + "> recebeu <@&" + roleId
                                    + "> (aprovado por <@" + approver.getId() + ">).");
                }, err -> event.reply("Falha ao atribuir o cargo: " + err.getMessage())
                        .setEphemeral(true).queue()),
                err -> event.reply("Não foi possível encontrar o membro solicitante.")
                        .setEphemeral(true).queue());
    }
}
