package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.giveaway.GiveawayService;
import dev.davimf.basebot.modules.base.giveaway.GiveawayWindow;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.OptionalLong;

/** /sorteio criar|encerrar|resortear (admin). */
public final class SorteioCommand implements SlashCommand {

    private final GiveawayService service;

    public SorteioCommand(GiveawayService service) { this.service = service; }

    @Override public String name() { return "sorteio"; }

    @Override public SlashCommandData data() {
        SubcommandData criar = new SubcommandData("criar", "Cria um sorteio")
                .addOptions(new OptionData(OptionType.STRING, "premio", "O que será sorteado", true))
                .addOptions(new OptionData(OptionType.STRING, "duracao", "Duração (ex.: 2h, 1d, 30m)", true))
                .addOptions(new OptionData(OptionType.INTEGER, "ganhadores", "Quantos ganhadores (default 1)", false))
                .addOptions(new OptionData(OptionType.INTEGER, "moedas", "Moedas por ganhador (opcional)", false))
                .addOptions(new OptionData(OptionType.ROLE, "cargo", "Requisito: ter este cargo", false))
                .addOptions(new OptionData(OptionType.INTEGER, "dias_servidor", "Requisito: dias no servidor", false))
                .addOptions(new OptionData(OptionType.INTEGER, "horas_call", "Requisito: horas em call", false))
                .addOptions(new OptionData(OptionType.STRING, "janela", "Requisito: call na faixa (ex.: 20-23)", false));
        SubcommandData encerrar = new SubcommandData("encerrar", "Encerra um sorteio agora")
                .addOptions(new OptionData(OptionType.STRING, "id", "ID do sorteio", true));
        SubcommandData resortear = new SubcommandData("resortear", "Sorteia novos ganhadores")
                .addOptions(new OptionData(OptionType.STRING, "id", "ID do sorteio", true));
        return Commands.slash("sorteio", "Sorteios do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(criar, encerrar, resortear);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "criar" -> criar(event, ctx);
            case "encerrar" -> Replies.ephemeral(event, ctx, service.endNow(strOpt(event, "id")));
            case "resortear" -> Replies.ephemeral(event, ctx, service.reroll(strOpt(event, "id")));
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    private void criar(SlashCommandInteractionEvent event, BotContext ctx) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            Replies.ephemeral(event, ctx, "Use num canal de texto.");
            return;
        }
        OptionalLong dur = Durations.parse(strOpt(event, "duracao"));
        if (dur.isEmpty()) {
            Replies.ephemeral(event, ctx, "Duração inválida. Ex.: `2h`, `1d`, `30m`.");
            return;
        }
        String janelaRaw = strOpt(event, "janela");
        int[] window = null;
        if (janelaRaw != null && !janelaRaw.isBlank()) {
            window = GiveawayWindow.parse(janelaRaw);
            if (window == null) {
                Replies.ephemeral(event, ctx, "Janela inválida. Ex.: `20-23` (0 a 24).");
                return;
            }
        }
        Role cargo = event.getOption("cargo", OptionMapping::getAsRole);
        int ganhadores = (int) longOpt(event, "ganhadores", 1);
        long moedas = longOpt(event, "moedas", 0);
        int dias = (int) longOpt(event, "dias_servidor", 0);
        int horas = (int) longOpt(event, "horas_call", 0);
        String id = service.create(event.getGuild(), channel, strOpt(event, "premio"), dur.getAsLong(),
                ganhadores, moedas, cargo == null ? null : cargo.getId(), dias, horas, window);
        Replies.ephemeral(event, ctx, "Sorteio criado! ID: `" + id + "`.");
    }

    private static String strOpt(SlashCommandInteractionEvent event, String name) {
        OptionMapping o = event.getOption(name);
        return o == null ? null : o.getAsString();
    }

    private static long longOpt(SlashCommandInteractionEvent event, String name, long def) {
        OptionMapping o = event.getOption(name);
        return o == null ? def : o.getAsLong();
    }
}
