package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EconomyFormat;
import dev.davimf.basebot.modules.base.economy.EconomyService;
import dev.davimf.basebot.modules.base.economy.WalletRepository;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** /eco (admin) — ajusta as moedas de um membro (carteira ou banco). */
public final class EcoCommand implements SlashCommand {

    private final EconomyService eco;

    public EcoCommand(EconomyService eco) { this.eco = eco; }

    @Override public String name() { return "eco"; }

    @Override public SlashCommandData data() {
        OptionData user = new OptionData(OptionType.USER, "usuario", "Membro", true);
        OptionData qtd = new OptionData(OptionType.INTEGER, "quantia", "Quantidade de moedas", true);
        OptionData dest = new OptionData(OptionType.STRING, "destino", "Carteira ou banco", false)
                .addChoice("Carteira", "carteira").addChoice("Banco", "banco");
        return Commands.slash("eco", "Gerencia as moedas de um membro (admin).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("add", "Adiciona moedas").addOptions(user, qtd, dest),
                        new SubcommandData("remove", "Remove moedas").addOptions(user, qtd, dest),
                        new SubcommandData("set", "Define o saldo").addOptions(user, qtd, dest),
                        new SubcommandData("reset", "Zera carteira e banco")
                                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro", true)));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        OptionMapping userOpt = event.getOption("usuario");
        User user = userOpt == null ? null : userOpt.getAsUser();
        if (user == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        String g = event.getGuild().getId();
        String u = user.getId();
        String sub = event.getSubcommandName() == null ? "" : event.getSubcommandName();
        OptionMapping qtdOpt = event.getOption("quantia");
        long qtd = qtdOpt == null ? 0 : qtdOpt.getAsLong();
        OptionMapping destOpt = event.getOption("destino");
        boolean bank = destOpt != null && "banco".equals(destOpt.getAsString());
        WalletRepository w = eco.wallets();

        switch (sub) {
            case "add" -> {
                if (bank) { w.addBank(g, u, qtd); } else { w.addCash(g, u, qtd); }
            }
            case "remove" -> {
                WalletRepository.Wallet cur = w.get(g, u);
                if (bank) { w.setBank(g, u, Math.max(0, cur.bank() - qtd)); }
                else { w.setCash(g, u, Math.max(0, cur.cash() - qtd)); }
            }
            case "set" -> {
                if (bank) { w.setBank(g, u, qtd); } else { w.setCash(g, u, qtd); }
            }
            case "reset" -> { w.setCash(g, u, 0); w.setBank(g, u, 0); }
            default -> {
                Replies.ephemeral(event, ctx, "Subcomando inválido.");
                return;
            }
        }
        WalletRepository.Wallet nw = w.get(g, u);
        Replies.reply(event, ctx, "Saldo de <@" + u + ">: carteira " + EconomyFormat.format(nw.cash(), cfg)
                + " · banco " + EconomyFormat.format(nw.bank(), cfg) + ".");
    }
}
