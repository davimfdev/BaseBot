// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: PdCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
                .addOption(OptionType.USER, "usuario", "Membro a remover", true)
                .addOption(OptionType.STRING, "motivo", "Motivo do PD", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.PUNICOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Punições**.");
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
        String reason = event.getOption("motivo", OptionMapping::getAsString);
        String targetTag = target.getUser().getAsTag();
        String targetId = target.getId();
        String actorId = event.getUser().getId();

        event.getGuild().kick(target)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "PD: " + reason)).queue(ok -> {
            ctx.database().actionLogs().log(event.getGuild().getId(), actorId, targetId, "PD", reason);
            String entry = "## " + Emojis.of(Emojis.SKULL, "💀") + " PD aplicado\n---\n"
                    + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · " + targetTag + " · `" + targetId + "`\n---\n"
                    + "" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Responsável** · <@" + actorId + ">\n"
                    + "" + Emojis.of(Emojis.NOTE, "📝") + " **Motivo** · " + reason;
            FacsLog.post(ctx, event.getGuild().getId(), "log-pds", entry);
            FacsLog.post(ctx, event.getGuild().getId(), "log-punicoes", entry);
            Replies.reply(event, ctx, Emojis.of(Emojis.SKULL, "💀") + " PD aplicado em " + targetTag + ".");
        }, err -> Replies.ephemeral(event, ctx, "Falha ao aplicar PD: " + err.getMessage()));
    }
}
