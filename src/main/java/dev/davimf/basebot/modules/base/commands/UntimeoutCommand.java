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
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /untimeout — removes a member's active timeout and marks the case inactive. */
public final class UntimeoutCommand implements SlashCommand {

    private final ModerationService service;

    public UntimeoutCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "untimeout";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("untimeout", "Remove o timeout de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true)
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
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente.");
            return;
        }
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        target.removeTimeout()
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason == null ? "Untimeout" : reason)).queue(
                ok -> {
                    service.deactivateLatest(event.getGuild().getId(), target.getId(), InfractionType.TIMEOUT);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.HOURGLASS, "⏳") + " Timeout de " + target.getAsMention() + " removido.");
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
