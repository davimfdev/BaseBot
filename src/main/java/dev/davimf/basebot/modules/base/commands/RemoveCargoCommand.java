package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
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
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (target == null || role == null) {
            event.reply("Membro ou cargo inválido.").setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canManageRole(event.getMember(), role, self)) {
            event.reply("Hierarquia insuficiente para gerenciar esse cargo.")
                    .setEphemeral(true).queue();
            return;
        }
        event.getGuild().removeRoleFromMember(target, role).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "REMOVE_ROLE", role.getId());
                    event.reply("Cargo " + role.getName() + " removido de "
                            + target.getUser().getAsTag()).queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
