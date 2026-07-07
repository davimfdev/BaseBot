// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: AddEmojiCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.EmojiNames;
import dev.davimf.basebot.util.ImageMedia;
import dev.davimf.basebot.util.Replies;
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
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String name = EmojiNames.sanitize(event.getOption("nome", OptionMapping::getAsString));
        if (!EmojiNames.isValid(name)) {
            Replies.ephemeral(event, ctx, "Nome de emoji inválido.");
            return;
        }
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        String link = event.getOption("link", OptionMapping::getAsString);
        if ((att == null) == (link == null)) {
            Replies.ephemeral(event, ctx, "Forneça **uma** imagem: um anexo OU um link.");
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
                        Replies.hook(event, ctx, "Emoji adicionado: " + emoji.getAsMention());
                    },
                    err -> {
                        fetched.erase();
                        Replies.hook(event, ctx, "Falha ao adicionar o emoji: " + err.getMessage());
                    });
        } catch (IOException e) {
            if (image != null) {
                image.erase();
            }
            Replies.hook(event, ctx, e.getMessage());
        }
    }
}
