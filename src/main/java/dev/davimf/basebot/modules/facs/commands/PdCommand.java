package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.FacsLog;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /pd — removes a member from the Discord (kick) and logs the action to both the PD log
 * channel and the Punishments log channel (BOTSPECS Module 4). Respects Discord role
 * hierarchy (actor + bot must outrank the target).
 */
public final class PdCommand implements SlashCommand {

    @Override
    public String name() {
        return "pd";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("pd", "Aplica PD: remove o membro do Discord e registra nos logs.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a remover", true)
                .addOption(OptionType.STRING, "motivo", "Motivo do PD", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Esse usuário não está no servidor.").setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self)) {
            event.reply("Hierarquia insuficiente: você ou o bot não estão acima desse membro.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        String targetTag = target.getUser().getAsTag();
        String targetId = target.getId();
        String actorId = event.getUser().getId();

        event.getGuild().kick(target).reason("PD: " + reason).queue(ok -> {
            ctx.database().actionLogs().log(event.getGuild().getId(), actorId, targetId, "PD", reason);
            String entry = "## 🚔 PD aplicado\n"
                    + "**Membro:** " + targetTag + " (`" + targetId + "`)\n"
                    + "**Responsável:** <@" + actorId + ">\n"
                    + "**Motivo:** " + reason;
            FacsLog.post(ctx, event.getGuild().getId(), "log-pds", entry);
            FacsLog.post(ctx, event.getGuild().getId(), "log-punicoes", entry);
            event.reply("🚔 PD aplicado em " + targetTag + ".").queue();
        }, err -> event.reply("Falha ao aplicar PD: " + err.getMessage()).setEphemeral(true).queue());
    }
}
