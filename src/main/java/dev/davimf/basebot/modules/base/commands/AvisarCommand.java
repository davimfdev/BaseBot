package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Infraction;
import dev.davimf.basebot.modules.base.moderation.ModerationConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /avisar — warns a member (records a WARN case + runs auto-escalation). */
public final class AvisarCommand implements SlashCommand {

    private final ModerationService service;

    public AvisarCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "avisar";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("avisar", "Aplica um aviso a um membro (registra no histórico).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a avisar", true)
                .addOption(OptionType.STRING, "motivo", "Motivo do aviso", false);
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
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (ModerationConfig.requireReason(cfg) && (reason == null || reason.isBlank())) {
            Replies.ephemeral(event, ctx, "Este servidor exige um **motivo** para avisos.");
            return;
        }
        Infraction c = service.warn(event.getGuild(), target, event.getUser().getId(), reason);
        Replies.reply(event, ctx, "" + Emojis.of(Emojis.WARN, "⚠️") + " Aviso aplicado em " + target.getAsMention() + " — Caso #" + c.caseNumber() + ".");
    }
}
