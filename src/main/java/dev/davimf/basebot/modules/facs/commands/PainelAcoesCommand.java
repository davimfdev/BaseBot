package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.actions.ActionView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /painel-acoes — opens the new-action modal that spawns an action panel (BOTSPECS Module 4). */
public final class PainelAcoesCommand implements SlashCommand {

    @Override
    public String name() {
        return "painel-acoes";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("painel-acoes", "Cria uma ação com lista de presença e reservas.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        event.replyModal(ActionView.createModal()).queue();
    }
}
