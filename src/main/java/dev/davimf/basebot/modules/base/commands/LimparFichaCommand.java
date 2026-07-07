package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /limparficha — limpa a ficha suja mediante pagamento (efêmero). */
public final class LimparFichaCommand implements SlashCommand {
    private final JailService jail;
    public LimparFichaCommand(JailService jail) { this.jail = jail; }

    @Override public String name() { return "limparficha"; }

    @Override public SlashCommandData data() {
        return Commands.slash("limparficha", "Limpa sua ficha suja mediante pagamento.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        Replies.ephemeral(event, ctx, jail.expunge(event.getGuild().getId(), event.getMember().getId()));
    }
}
