package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.OptionalLong;

/** /slowmode — sets a channel's slowmode (0/off to clear). */
public final class SlowmodeCommand implements SlashCommand {

    /** Discord's maximum slowmode is 6 hours. */
    private static final int MAX_SECONDS = 21_600;

    @Override
    public String name() {
        return "slowmode";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("slowmode", "Define o modo lento de um canal (0 para desativar).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL))
                .addOption(OptionType.STRING, "tempo", "Ex: 5s, 30s, 1m, 1h — ou 0 para desativar", true)
                .addOption(OptionType.CHANNEL, "canal", "Canal (padrão: o atual)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping canalOpt = event.getOption("canal");
        TextChannel channel = canalOpt != null && canalOpt.getAsChannel() instanceof TextChannel tc
                ? tc
                : (event.getChannel() instanceof TextChannel cur ? cur : null);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Selecione um canal de texto.");
            return;
        }
        String tempo = event.getOption("tempo", OptionMapping::getAsString).trim().toLowerCase();
        int seconds;
        if (tempo.equals("0") || tempo.equals("off") || tempo.equals("0s")) {
            seconds = 0;
        } else {
            OptionalLong millis = Durations.parse(tempo);
            if (millis.isEmpty()) {
                Replies.ephemeral(event, ctx, "Tempo inválido. Use `5s`, `30s`, `1m`, `1h`… ou `0`.");
                return;
            }
            seconds = (int) Math.min(MAX_SECONDS, millis.getAsLong() / 1000);
        }
        channel.getManager().setSlowmode(seconds)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "/slowmode")).queue(
                ok -> Replies.reply(event, ctx, seconds == 0
                        ? "" + Emojis.of(Emojis.SLOWMODE, "🐢") + " Modo lento desativado em " + channel.getAsMention() + "."
                        : "" + Emojis.of(Emojis.SLOWMODE, "🐢") + " Modo lento de " + channel.getAsMention() + " definido para `"
                                + Durations.format(seconds * 1000L) + "`."),
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
