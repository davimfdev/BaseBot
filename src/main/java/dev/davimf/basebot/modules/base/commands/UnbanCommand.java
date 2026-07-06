// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: UnbanCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unban — lifts a ban by user. */
public final class UnbanCommand implements SlashCommand {

    private final ModerationService service;

    public UnbanCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "unban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unban", "Remove o banimento de um usuário.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a desbanir", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        event.getGuild().unban(target).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "UNBAN", null);
                    service.deactivateLatest(event.getGuild().getId(), target.getId(), InfractionType.BAN);
                    service.deactivateLatest(event.getGuild().getId(), target.getId(), InfractionType.TEMPBAN);
                    Replies.reply(event, ctx, "Banimento removido: " + target.getAsTag());
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao desbanir: " + err.getMessage()));
    }
}
