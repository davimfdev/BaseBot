package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /pagar — transfere moedas da sua carteira para outro membro. */
public final class PagarCommand implements SlashCommand {
    private final EconomyService eco;
    public PagarCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "pagar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("pagar", "Transfere moedas para outro membro.")
                .addOption(OptionType.USER, "usuario", "Quem vai receber", true)
                .addOption(OptionType.INTEGER, "quantia", "Quanto pagar", true);
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
        OptionMapping opt = event.getOption("usuario");
        Member target = opt == null ? null : opt.getAsMember();
        OptionMapping qtdOpt = event.getOption("quantia");
        long qtd = qtdOpt == null ? 0 : qtdOpt.getAsLong();
        if (target == null) {
            Replies.ephemeral(event, ctx, "Destinatário inválido ou fora do servidor.");
            return;
        }
        Replies.reply(event, ctx, eco.pay(event.getGuild(), event.getMember(), target, qtd));
    }
}
