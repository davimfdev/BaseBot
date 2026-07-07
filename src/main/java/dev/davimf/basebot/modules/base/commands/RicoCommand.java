package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.economy.RicoView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /rico — ranking de riqueza do servidor (efêmero, paginado). */
public final class RicoCommand implements SlashCommand {
    private final EconomyService eco;
    public RicoCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "rico"; }

    @Override public SlashCommandData data() {
        return Commands.slash("rico", "Ranking dos mais ricos do servidor.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        int total = eco.wallets().count(guildId);
        var entries = eco.wallets().topPage(guildId, RicoView.PAGE, 0);
        event.replyComponents(RicoView.panel(EmbedColor.resolve(cfg), entries, 0, total, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
