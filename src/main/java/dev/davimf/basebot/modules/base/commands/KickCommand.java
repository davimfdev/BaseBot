// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: KickCommand
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
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /kick — removes a member, validating moderator + bot hierarchy (BOTSPECS Module 1). */
public final class KickCommand implements SlashCommand {

    private final ModerationService service;

    public KickCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "kick";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("kick", "Expulsa um membro do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a expulsar", true)
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
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self)) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente: você ou o bot não estão acima desse membro.");
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        event.getGuild().kick(target)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason)).queue(
                ok -> {
                    var c = service.record(event.getGuild(), InfractionType.KICK, target.getUser(),
                            event.getUser().getId(), reason, null, null);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.KICK, "👢") + " Membro expulso: " + target.getUser().getAsTag()
                            + " — Caso #" + c.caseNumber() + ".");
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao expulsar: " + err.getMessage()));
    }
}
