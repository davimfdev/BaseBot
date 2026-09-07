package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.ratelimit.ProfileRateLimiter;
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

/**
 * /bot-icon — changes the GLOBAL bot avatar (max 2/hour; BOTSPECS Module 1).
 *
 * <p>Accepts an attachment OR a URL; either way the bot downloads the image and uploads
 * the bytes to Discord (so Discord hosts the avatar permanently), then erases the bytes.
 */
public final class BotIconCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-icon";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-icon", "Altera o avatar global do bot (limite: 2x por hora).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.ATTACHMENT, "imagem", "Arquivo de imagem (PNG/JPG/GIF/WEBP)", false)
                .addOption(OptionType.STRING, "link", "URL de uma imagem", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        String link = event.getOption("link", OptionMapping::getAsString);
        if ((att == null) == (link == null)) {
            Replies.ephemeral(event, ctx, "Forneça **uma** imagem: um anexo OU um link.");
            return;
        }

        ProfileRateLimiter.Decision d = ctx.profileRateLimiter().check("icon", System.currentTimeMillis());
        if (!d.allowed()) {
            long mins = (d.retryAfterMillis() + 59_999) / 60_000;
            Replies.ephemeral(event, ctx,
                    "Limite de 2 alterações por hora atingido. Tente novamente em ~" + mins + " min.");
            return;
        }

        event.deferReply(true).queue();
        // Download blocks -> run off the JDA event thread.
        ctx.scheduler().executor().execute(() -> applyAvatar(event, ctx, att, link));
    }

    private void applyAvatar(SlashCommandInteractionEvent event, BotContext ctx,
                             Message.Attachment att, String link) {
        ImageMedia.Image image = null;
        try {
            image = (att != null) ? ImageMedia.fromAttachment(att) : ImageMedia.fromUrl(link);
            Icon icon = Icon.from(image.bytes());
            ImageMedia.Image fetched = image;
            String guildId = event.getGuild() == null ? null : event.getGuild().getId();
            event.getJDA().getSelfUser().getManager().setAvatar(icon).queue(
                    ok -> {
                        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null,
                                "BOT_ICON", fetched.fileName());
                        fetched.erase();
                        Replies.hook(event, ctx, "Avatar global atualizado. "
                                + "(Afeta todos os servidores; limite do Discord: 2x por hora.)");
                    },
                    err -> {
                        fetched.erase();
                        Replies.hook(event, ctx, "Falha ao atualizar o avatar: " + err.getMessage());
                    });
        } catch (IOException e) {
            if (image != null) {
                image.erase();
            }
            Replies.hook(event, ctx, e.getMessage());
        }
    }
}
