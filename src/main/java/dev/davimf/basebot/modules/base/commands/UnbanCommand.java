package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unban — lifts a ban by user. */
public final class UnbanCommand implements SlashCommand {

    @Override
    public String name() {
        return "unban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unban", "Remove o banimento de um usuário.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a desbanir", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            event.reply("Usuário inválido.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().unban(target).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "UNBAN", null);
                    event.reply("Banimento removido: " + target.getAsTag()).queue();
                },
                err -> event.reply("Falha ao desbanir: " + err.getMessage()).setEphemeral(true).queue());
    }
}
