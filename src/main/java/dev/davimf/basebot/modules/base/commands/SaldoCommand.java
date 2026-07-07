package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.economy.SaldoView;
import dev.davimf.basebot.modules.base.economy.WalletRepository;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /saldo — carteira e banco (efêmero). */
public final class SaldoCommand implements SlashCommand {
    private final EconomyService eco;
    public SaldoCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "saldo"; }

    @Override public SlashCommandData data() {
        return Commands.slash("saldo", "Mostra seu saldo (carteira e banco).")
                .addOption(OptionType.USER, "usuario", "Membro (opcional)", false);
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
        Member target = opt == null ? event.getMember() : opt.getAsMember();
        if (target == null) {
            Replies.ephemeral(event, ctx, "Esse usuário não está no servidor.");
            return;
        }
        WalletRepository.Wallet w = eco.wallets().get(event.getGuild().getId(), target.getId());
        int rank = eco.wallets().rank(event.getGuild().getId(), target.getId());
        event.replyComponents(SaldoView.panel(EmbedColor.resolve(cfg), target, w, rank, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
