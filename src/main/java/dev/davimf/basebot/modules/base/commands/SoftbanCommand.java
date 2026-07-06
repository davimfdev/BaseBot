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

/** /softban — ban + immediate unban to purge a user's recent messages; records a SOFTBAN case. */
public final class SoftbanCommand implements SlashCommand {

    private final ModerationService service;

    public SoftbanCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "softban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("softban", "Bane e desbane na hora para limpar as mensagens recentes do usuário.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false)
                .addOption(OptionType.INTEGER, "dias", "Dias de mensagens a apagar (1-7)", false);
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
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        int days = (int) Math.max(1, Math.min(7, event.getOption("dias", 1L, OptionMapping::getAsLong)));
        event.getGuild().ban(target, days, TimeUnit.DAYS)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason == null ? "Softban" : reason)).queue(
                ok -> event.getGuild().unban(target)
                        .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Softban (desbane imediato)")).queue(
                        ok2 -> {
                            var c = service.record(event.getGuild(), InfractionType.SOFTBAN, target,
                                    event.getUser().getId(), reason, null, null);
                            Replies.reply(event, ctx, "" + Emojis.of(Emojis.BROOM, "🧹") + " Softban aplicado em " + target.getAsTag()
                                    + " — Caso #" + c.caseNumber() + ".");
                        },
                        err -> Replies.ephemeral(event, ctx, "Banido, mas falha ao desbanir: " + err.getMessage())),
                err -> Replies.ephemeral(event, ctx, "Falha ao banir: " + err.getMessage()));
    }
}
