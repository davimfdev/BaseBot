package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /sacar — banco → carteira (sem valor = tudo). */
public final class SacarCommand implements SlashCommand {
    private final EconomyService eco;
    public SacarCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "sacar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("sacar", "Saca moedas do banco para a carteira.")
                .addOption(OptionType.INTEGER, "quantia", "Quanto sacar (vazio = tudo)", false);
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
        OptionMapping q = event.getOption("quantia");
        long amt = q == null ? 0 : q.getAsLong();
        Replies.reply(event, ctx, eco.withdraw(event.getGuild(), event.getMember(), amt));
    }
}
