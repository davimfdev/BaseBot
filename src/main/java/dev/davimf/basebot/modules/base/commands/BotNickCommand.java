// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: BotNickCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * /bot-nick — changes the bot's nickname ONLY in the current guild (BOTSPECS Module 1
 * Golden Rule: this never touches the global profile, so it is not rate-limited).
 */
public final class BotNickCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-nick";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-nick", "Altera o apelido do bot apenas neste servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.NICKNAME_MANAGE))
                .addOption(OptionType.STRING, "apelido", "Novo apelido (vazio para remover)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String apelido = event.getOption("apelido", OptionMapping::getAsString);
        event.getGuild().getSelfMember().modifyNickname(apelido)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Apelido do bot")).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), null, "BOT_NICK", apelido);
                    Replies.reply(event, ctx, apelido == null || apelido.isBlank()
                            ? "Apelido removido neste servidor."
                            : "Apelido alterado para **" + apelido + "** neste servidor.");
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
