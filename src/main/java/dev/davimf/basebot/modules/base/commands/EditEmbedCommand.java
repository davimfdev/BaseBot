package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.embed.EmbedView;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /editembed — edits an embed previously sent by the bot's webhook in this channel,
 * preserving any select menus on the message (BOTSPECS Module 1).
 */
public final class EditEmbedCommand implements SlashCommand {

    @Override
    public String name() {
        return "editembed";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("editembed", "Edita um embed enviado pelo bot (preserva os menus).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.STRING, "mensagem", "ID da mensagem do embed", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String messageId = event.getOption("mensagem", OptionMapping::getAsString).trim();
        if (!messageId.matches("\\d{15,25}")) {
            event.reply("ID de mensagem inválido.").setEphemeral(true).queue();
            return;
        }
        event.replyModal(EmbedView.edit(messageId)).queue();
    }
}
