package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.ratelimit.ProfileRateLimiter;
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
import java.io.InputStream;

/** /bot-icon — changes the GLOBAL bot avatar (max 2/hour; BOTSPECS Module 1). */
public final class BotIconCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-icon";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-icon", "Altera o avatar global do bot (limite: 2x por hora).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.ATTACHMENT, "imagem", "Imagem (PNG/JPG/GIF)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        Message.Attachment att = event.getOption("imagem", OptionMapping::getAsAttachment);
        if (att == null || !att.isImage()) {
            event.reply("Envie uma imagem válida.").setEphemeral(true).queue();
            return;
        }
        ProfileRateLimiter.Decision d = ctx.profileRateLimiter().check("icon", System.currentTimeMillis());
        if (!d.allowed()) {
            long mins = (d.retryAfterMillis() + 59_999) / 60_000;
            event.reply("Limite de 2 alterações por hora atingido. Tente novamente em ~" + mins + " min.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        att.getProxy().download().thenAccept(stream -> {
            try (InputStream in = stream) {
                Icon icon = Icon.from(in);
                event.getJDA().getSelfUser().getManager().setAvatar(icon).queue(
                        ok -> {
                            ctx.database().actionLogs().log(
                                    event.getGuild() == null ? null : event.getGuild().getId(),
                                    event.getUser().getId(), null, "BOT_ICON", att.getFileName());
                            event.getHook().sendMessage("Avatar global atualizado. "
                                    + "(Afeta todos os servidores; limite do Discord: 2x por hora.)").queue();
                        },
                        err -> event.getHook().sendMessage("Falha ao atualizar o avatar: "
                                + err.getMessage()).queue());
            } catch (IOException e) {
                event.getHook().sendMessage("Falha ao processar a imagem.").queue();
            }
        }).exceptionally(t -> {
            event.getHook().sendMessage("Falha ao baixar a imagem.").queue();
            return null;
        });
    }
}
