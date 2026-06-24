package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.sets.SetRequestView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * /solicitar-cargo — a member requests a role; the request is posted to the Set-requests
 * log channel for approval by someone higher in the hierarchy (BOTSPECS Module 4).
 */
public final class SolicitarCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "solicitar-cargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("solicitar-cargo", "Solicita um cargo; aprovação por hierarquia superior.")
                .addOption(OptionType.ROLE, "cargo", "Cargo solicitado", true)
                .addOption(OptionType.STRING, "motivo", "Motivo do pedido", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (role == null) {
            event.reply("Cargo inválido.").setEphemeral(true).queue();
            return;
        }
        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.reply("Não posso atribuir esse cargo (acima do meu cargo mais alto).")
                    .setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String channelId = cfg.channel("log-sets");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            event.reply("Canal de solicitações de Set não configurado em /setup → Logs.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        channel.sendMessageComponents(SetRequestView.request(
                        EmbedColor.resolve(cfg), event.getUser().getId(), role.getId(), reason))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                            role.getId(), "SET_REQUEST", reason);
                    event.reply("🎖️ Solicitação enviada para análise.").setEphemeral(true).queue();
                }, err -> event.reply("Falha ao enviar solicitação: " + err.getMessage())
                        .setEphemeral(true).queue());
    }
}
