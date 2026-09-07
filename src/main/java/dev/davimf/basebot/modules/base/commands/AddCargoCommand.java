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

/** /addcargo — gives a member a role, validating actor + bot hierarchy (BOTSPECS Module 1). */
public final class AddCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "addcargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("addcargo", "Adiciona um cargo a um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.ROLE, "cargo", "Cargo a adicionar", true);
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
        event.getGuild().addRoleToMember(target, role)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Cargo adicionado via comando")).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "ADD_ROLE", role.getId());
                    Replies.reply(event, ctx, "Cargo " + role.getName() + " adicionado a "
                            + target.getUser().getAsTag());
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
