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

/** /fianca — paga fiança pra sair da cadeia sem sujar a ficha (efêmero). */
public final class FiancaCommand implements SlashCommand {
    private final JailService jail;
    public FiancaCommand(JailService jail) { this.jail = jail; }

    @Override public String name() { return "fianca"; }

    @Override public SlashCommandData data() {
        return Commands.slash("fianca", "Paga a fiança pra sair da cadeia agora.");
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
        Replies.ephemeral(event, ctx, jail.bail(event.getGuild().getId(), event.getMember().getId()));
    }
}
