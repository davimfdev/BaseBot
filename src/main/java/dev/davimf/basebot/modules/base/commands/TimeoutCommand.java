package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Duration;
import java.util.OptionalLong;

/** /timeout — native Discord timeout (the default mute), up to 28 days; records a TIMEOUT case. */
public final class TimeoutCommand implements SlashCommand {

    private final ModerationService service;

    public TimeoutCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "timeout";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("timeout", "Silencia um membro (texto e voz) por um tempo, até 28 dias.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.STRING, "tempo", "Duração (ex: 10m, 1h, 1d)", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Esse usuário não está no servidor.");
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
            return;
        }
        OptionalLong dur = Durations.parse(event.getOption("tempo", OptionMapping::getAsString));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Use algo como `10m`, `1h` ou `1d` (máx 28 dias).");
            return;
        }
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (ModerationConfig.requireReason(cfg) && (reason == null || reason.isBlank())) {
            Replies.ephemeral(event, ctx, "Este servidor exige um **motivo**.");
            return;
        }
        long millis = dur.getAsLong();
        target.timeoutFor(Duration.ofMillis(millis))
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason == null ? "Timeout" : reason)).queue(
                ok -> {
                    var c = service.record(event.getGuild(), InfractionType.TIMEOUT, target.getUser(),
                            event.getUser().getId(), reason, System.currentTimeMillis() + millis, millis);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.HOURGLASS, "⏳") + " " + target.getAsMention() + " silenciado por "
                            + Durations.format(millis) + " — Caso #" + c.caseNumber() + ".");
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao aplicar timeout: " + err.getMessage()));
    }
}
