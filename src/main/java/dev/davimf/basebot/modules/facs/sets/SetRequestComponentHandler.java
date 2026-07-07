// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.sets
// 
// Class: SetRequestComponentHandler
// 
// Methods:
//   - `Method` : `public String namespace()`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.sets;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
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
        if (!ManagerPermissions.can(event.getMember(),
                ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()),
                ManagerPermissions.Capability.RECRUTAMENTO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Recrutamento** pode resolver Sets.");
            return;
        }
        String requesterId = id.arg(0);
        String roleId = id.arg(1);
        Role role = event.getGuild().getRoleById(roleId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));

        if ("reject".equals(id.action())) {
            event.editComponents(SetRequestView.resolved(accent, requesterId, roleId,
                    "" + Emojis.of(Emojis.CHECK_NO, "❌") + " Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    requesterId, "SET_REJECT", roleId);
            return;
        }
        if (!"approve".equals(id.action())) {
            return;
        }
        if (role == null) {
            Replies.ephemeral(event, ctx, "Esse cargo não existe mais.");
            return;
        }
        Member approver = event.getMember();
        if (approver.getId().equals(requesterId)) {
            Replies.ephemeral(event, ctx, "Você não pode aprovar a sua própria solicitação.");
            return;
        }
        if (!Moderation.canManageRole(approver, role, event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Você precisa estar acima desse cargo na hierarquia para aprovar.");
            return;
        }
        event.getGuild().retrieveMemberById(requesterId).queue(member ->
                event.getGuild().addRoleToMember(member, role).reason("Set aprovado por "
                        + approver.getUser().getName()).queue(ok -> {
                    event.editComponents(SetRequestView.resolved(accent, requesterId, roleId,
                            "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Aprovado por <@" + approver.getId() + ">.")).useComponentsV2().queue();
                    ctx.database().actionLogs().log(event.getGuild().getId(), approver.getId(),
                            requesterId, "SET_APPROVE", roleId);
                    FacsLog.post(ctx, event.getGuild().getId(), "log-hierarquia",
                            "## " + Emojis.of(Emojis.RANK, "🎖️") + " Set aprovado\n---\n"
                                    + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + requesterId + ">\n"
                                    + "" + Emojis.of(Emojis.ROLES, "🎭") + " **Cargo** · <@&" + roleId + ">\n---\n"
                                    + "" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Aprovado por** · <@" + approver.getId() + ">");
                }, err -> Replies.ephemeral(event, ctx, "Falha ao atribuir o cargo: " + err.getMessage())),
                err -> Replies.ephemeral(event, ctx, "Não foi possível encontrar o membro solicitante."));
    }
}
