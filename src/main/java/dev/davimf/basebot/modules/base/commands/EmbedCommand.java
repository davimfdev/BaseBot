package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.embed.EmbedView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /embed — opens the embed builder modal; sent via webhook with optional impersonation. */
public final class EmbedCommand implements SlashCommand {

    @Override
    public String name() {
        return "embed";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("embed", "Cria e envia um embed (com personificação via webhook).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        event.replyModal(EmbedView.compose()).queue();
    }
}
