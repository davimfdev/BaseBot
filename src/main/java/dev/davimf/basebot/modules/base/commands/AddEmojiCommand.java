package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.EmojiNames;
import dev.davimf.basebot.util.ImageMedia;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.io.IOException;

/** /addemoji — adds a custom emoji from a link or file (BOTSPECS Module 1). */
public final class AddEmojiCommand implements SlashCommand {

    @Override
    public String name() {
        return "addemoji";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("addemoji", "Adiciona um emoji personalizado ao servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_GUILD_EXPRESSIONS))
                .addOption(OptionType.STRING, "nome", "Nome do emoji (2-32, letras/números/_)", true)
                .addOption(OptionType.ATTACHMENT, "imagem", "Arquivo de imagem", false)
                .addOption(OptionType.STRING, "link", "URL de uma imagem", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String name = EmojiNames.sanitize(event.getOption("nome", OptionMapping::getAsString));
        if (!EmojiNames.isValid(name)) {
            event.reply("Nome de emoji inválido.").setEphemeral(true).queue();
            return;
        }
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        String link = event.getOption("link", OptionMapping::getAsString);
        if ((att == null) == (link == null)) {
            event.reply("Forneça **uma** imagem: um anexo OU um link.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        ctx.scheduler().executor().execute(() -> createEmoji(event, ctx, name, att, link));
    }

    private void createEmoji(SlashCommandInteractionEvent event, BotContext ctx,
                             String name, Message.Attachment att, String link) {
        ImageMedia.Image image = null;
        try {
            image = (att != null) ? ImageMedia.fromAttachment(att) : ImageMedia.fromUrl(link);
            Icon icon = Icon.from(image.bytes());
            ImageMedia.Image fetched = image;
            event.getGuild().createEmoji(name, icon).queue(
                    emoji -> {
                        ctx.database().actionLogs().log(event.getGuild().getId(),
                                event.getUser().getId(), emoji.getId(), "ADD_EMOJI", name);
                        fetched.erase();
                        event.getHook().sendMessage("Emoji adicionado: " + emoji.getAsMention()).queue();
                    },
                    err -> {
                        fetched.erase();
                        event.getHook().sendMessage("Falha ao adicionar o emoji: " + err.getMessage()).queue();
                    });
        } catch (IOException e) {
            if (image != null) {
                image.erase();
            }
            event.getHook().sendMessage(e.getMessage()).queue();
        }
    }
}
