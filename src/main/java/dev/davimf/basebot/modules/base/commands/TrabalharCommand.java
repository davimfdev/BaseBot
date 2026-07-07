package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.economy.JailService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /trabalhar — ganho aleatório com cooldown. */
public final class TrabalharCommand implements SlashCommand {
    private final EconomyService eco;
    private final JailService jail;
    public TrabalharCommand(EconomyService eco, JailService jail) { this.eco = eco; this.jail = jail; }

    @Override public String name() { return "trabalhar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("trabalhar", "Trabalhe para ganhar moedas.");
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
        if (jail.blockedIfJailed(event, ctx, event.getGuild().getId(), event.getMember().getId())) {
            return;
        }
        Replies.reply(event, ctx, eco.work(event.getGuild(), event.getMember()));
    }
}
