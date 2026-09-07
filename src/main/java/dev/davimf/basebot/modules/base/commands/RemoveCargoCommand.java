package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /removecargo — removes a role from a member, validating actor + bot hierarchy. */
public final class RemoveCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "removecargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("removecargo", "Remove um cargo de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.ROLE, "cargo", "Cargo a remover", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (target == null || role == null) {
            Replies.ephemeral(event, ctx, "Membro ou cargo inválido.");
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canManageRole(event.getMember(), role, self)) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente para gerenciar esse cargo.");
            return;
        }
        event.getGuild().removeRoleFromMember(target, role)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Cargo removido via comando")).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "REMOVE_ROLE", role.getId());
                    Replies.reply(event, ctx, "Cargo " + role.getName() + " removido de "
                            + target.getUser().getAsTag());
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
