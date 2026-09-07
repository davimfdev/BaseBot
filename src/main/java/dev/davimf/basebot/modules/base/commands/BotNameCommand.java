package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.ratelimit.ProfileRateLimiter;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /bot-name — changes the GLOBAL bot username (max 2/hour; BOTSPECS Module 1). */
public final class BotNameCommand implements SlashCommand {

    @Override
    public String name() {
        return "bot-name";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("bot-name", "Altera o nome global do bot (limite: 2x por hora).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addOption(OptionType.STRING, "nome", "Novo nome global (2-32 caracteres)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        String nome = event.getOption("nome", OptionMapping::getAsString);
        if (nome == null || nome.length() < 2 || nome.length() > 32) {
            Replies.ephemeral(event, ctx, "O nome deve ter entre 2 e 32 caracteres.");
            return;
        }
        ProfileRateLimiter.Decision d = ctx.profileRateLimiter().check("name", System.currentTimeMillis());
        if (!d.allowed()) {
            long mins = (d.retryAfterMillis() + 59_999) / 60_000;
            Replies.ephemeral(event, ctx,
                    "Limite de 2 alterações por hora atingido. Tente novamente em ~" + mins + " min.");
            return;
        }
        event.getJDA().getSelfUser().getManager().setName(nome).queue(
                ok -> {
                    ctx.database().actionLogs().log(
                            event.getGuild() == null ? null : event.getGuild().getId(),
                            event.getUser().getId(), null, "BOT_NAME", nome);
                    Replies.reply(event, ctx, "Nome global alterado para **" + nome + "**. "
                            + "(Afeta todos os servidores; limite do Discord: 2x por hora.)");
                },
                err -> Replies.ephemeral(event, ctx, "Falha ao alterar o nome: " + err.getMessage()));
    }
}
