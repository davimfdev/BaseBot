// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: BanCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
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

import java.util.concurrent.TimeUnit;

/** /ban — bans a user (works by ID even if they left), validating hierarchy when present. */
public final class BanCommand implements SlashCommand {

    private final ModerationService service;

    public BanCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "ban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ban", "Bane um usuário do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a banir", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false)
                .addOption(OptionType.INTEGER, "dias", "Dias de mensagens a apagar (0-7)", false);
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
        Member self = event.getGuild().getSelfMember();
        if (targetMember != null && !Moderation.canModerate(event.getMember(), targetMember, self)) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        long days = event.getOption("dias", 0L, OptionMapping::getAsLong);
        int clamped = (int) Math.max(0, Math.min(7, days));

        event.getGuild().ban(target, clamped, TimeUnit.DAYS)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason)).queue(
                ok -> {
                    var c = service.record(event.getGuild(), InfractionType.BAN, target,
                            event.getUser().getId(), reason, null, null);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.BAN, "🔨") + " Usuário banido: " + target.getAsTag() + " — Caso #" + c.caseNumber() + ".");
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao banir: " + err.getMessage()));
    }
}
