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
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/** /tempban — temporary ban that the scheduler auto-lifts at expiry; records a TEMPBAN case. */
public final class TempbanCommand implements SlashCommand {

    private final ModerationService service;

    public TempbanCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "tempban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("tempban", "Bane um usuário temporariamente (removido automaticamente).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a banir", true)
                .addOption(OptionType.STRING, "tempo", "Duração (ex: 1h, 7d, até 28d)", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        Member targetMember = event.getOption("usuario", OptionMapping::getAsMember);
        if (targetMember != null && !Moderation.canModerate(event.getMember(), targetMember,
                event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
            return;
        }
        OptionalLong dur = Durations.parse(event.getOption("tempo", OptionMapping::getAsString));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Use algo como `1h`, `7d` (máx 28 dias).");
            return;
        }
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (ModerationConfig.requireReason(cfg) && (reason == null || reason.isBlank())) {
            Replies.ephemeral(event, ctx, "Este servidor exige um **motivo**.");
            return;
        }
        long millis = dur.getAsLong();
        event.getGuild().ban(target, 0, TimeUnit.SECONDS)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason == null ? "Tempban" : reason)).queue(
                ok -> {
                    var c = service.record(event.getGuild(), InfractionType.TEMPBAN, target,
                            event.getUser().getId(), reason, System.currentTimeMillis() + millis, millis);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.TIMER, "⏲️") + " " + target.getAsTag() + " banido por "
                            + Durations.format(millis) + " — Caso #" + c.caseNumber() + ".");
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao banir: " + err.getMessage()));
    }
}
