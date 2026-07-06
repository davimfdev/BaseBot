// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: RelatorioCommand
// 
// Constructors:
//   - `Constructor` : `public RelatorioCommand(EconomyRepository economy)`
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
//   - `Method` : `private static String csv(String value)`
// 
// Fields:
//   - `Field` : `private static final int EMBED_MAX_DAYS`
//   - `Field` : `private static final int EMBED_ROWS`
//   - `Field` : `private final EconomyRepository economy`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.FacTransaction;
import dev.davimf.basebot.database.sqlite.ActionLogRepository;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.economy.EconomyRepository;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * /relatorio — financial or action reports (BOTSPECS Module 4). Embeds are capped at the
 * last 7 days; CSV export is unlimited ({@code dias} 0 = all time).
 */
public final class RelatorioCommand implements SlashCommand {

    private static final int EMBED_MAX_DAYS = 7;
    private static final int EMBED_ROWS = 15;

    private final EconomyRepository economy;

    public RelatorioCommand(EconomyRepository economy) {
        this.economy = economy;
    }

    @Override
    public String name() {
        return "relatorio";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("relatorio", "Relatórios financeiros e de ações.")
                .addOptions(new OptionData(OptionType.STRING, "tipo", "O que reportar", true)
                        .addChoice("Financeiro", "financeiro").addChoice("Ações", "acoes"))
                .addOptions(new OptionData(OptionType.STRING, "formato", "Como entregar", true)
                        .addChoice("Embed (até 7 dias)", "embed").addChoice("CSV (sem limite)", "csv"))
                .addOptions(new OptionData(OptionType.INTEGER, "dias", "Período em dias (0 = tudo no CSV)", false)
                        .setMinValue(0).setMaxValue(3650));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão **Financeiro** para gerar relatórios.");
            return;
        }
        String guildId = event.getGuild().getId();
        String tipo = event.getOption("tipo", OptionMapping::getAsString);
        boolean csv = "csv".equals(event.getOption("formato", OptionMapping::getAsString));
        long diasOpt = event.getOption("dias", csv ? 30L : (long) EMBED_MAX_DAYS, OptionMapping::getAsLong);
        int dias = (int) diasOpt;
        if (!csv) {
            dias = dias <= 0 ? EMBED_MAX_DAYS : Math.min(dias, EMBED_MAX_DAYS);
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));

        if ("financeiro".equals(tipo)) {
            financeiro(event, guildId, dias, csv, accent);
        } else {
            acoes(event, ctx.database().actionLogs(), guildId, dias, csv, accent);
        }
    }

    private void financeiro(SlashCommandInteractionEvent event, String guildId, int dias,
                            boolean csv, int accent) {
        List<FacTransaction> txs = economy.listTransactions(guildId, dias);
        if (csv) {
            StringBuilder sb = new StringBuilder("created_at,type,amount_cents,actor_id,note\n");
            for (FacTransaction t : txs) {
                sb.append(csv(t.createdAt())).append(',').append(csv(t.type())).append(',')
                        .append(t.amountCents()).append(',').append(csv(t.actorId())).append(',')
                        .append(csv(t.note())).append('\n');
            }
            replyCsv(event, "relatorio-financeiro.csv", sb.toString());
            return;
        }
        long credits = txs.stream().filter(t -> t.amountCents() > 0).mapToLong(FacTransaction::amountCents).sum();
        long debits = txs.stream().filter(t -> t.amountCents() < 0).mapToLong(FacTransaction::amountCents).sum();
        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.STATS, "📊") + " Relatório Financeiro (").append(dias).append("d)\n")
                .append("**Saldo atual:** ").append(Money.format(economy.getBalance(guildId))).append('\n')
                .append("**Entradas:** ").append(Money.format(credits)).append('\n')
                .append("**Saídas:** ").append(Money.format(debits)).append('\n')
                .append("**Movimentos:** ").append(txs.size());
        int shown = Math.min(EMBED_ROWS, txs.size());
        for (int i = 0; i < shown; i++) {
            FacTransaction t = txs.get(i);
            body.append("\n• ").append(t.createdAt()).append(" · ").append(t.type()).append(" · ")
                    .append(Money.format(t.amountCents()));
        }
        replyEmbed(event, accent, body.toString());
    }

    private void acoes(SlashCommandInteractionEvent event, ActionLogRepository logs, String guildId,
                       int dias, boolean csv, int accent) {
        List<ActionLogRepository.Entry> rows = logs.listSince(guildId, dias);
        if (csv) {
            StringBuilder sb = new StringBuilder("created_at,action,actor_id,target_id,detail\n");
            for (ActionLogRepository.Entry r : rows) {
                sb.append(csv(r.createdAt())).append(',').append(csv(r.action())).append(',')
                        .append(csv(r.actorId())).append(',').append(csv(r.targetId())).append(',')
                        .append(csv(r.detail())).append('\n');
            }
            replyCsv(event, "relatorio-acoes.csv", sb.toString());
            return;
        }
        StringBuilder body = new StringBuilder("## " + Emojis.of(Emojis.STATS, "📊") + " Relatório de Ações (").append(dias).append("d)\n")
                .append("**Registros:** ").append(rows.size());
        int shown = Math.min(EMBED_ROWS, rows.size());
        for (int i = 0; i < shown; i++) {
            ActionLogRepository.Entry r = rows.get(i);
            body.append("\n• ").append(r.createdAt()).append(" · ").append(r.action())
                    .append(r.actorId() == null ? "" : " · por " + r.actorId());
        }
        replyEmbed(event, accent, body.toString());
    }

    private static void replyEmbed(SlashCommandInteractionEvent event, int accent, String body) {
        String trimmed = body.length() > 3900 ? body.substring(0, 3900) + "\n-# (truncado)" : body;
        event.replyComponents(Panels.container(accent, Panels.text(trimmed)))
                .useComponentsV2().setEphemeral(true).queue();
    }

    private static void replyCsv(SlashCommandInteractionEvent event, String filename, String content) {
        FileUpload file = FileUpload.fromData(content.getBytes(StandardCharsets.UTF_8), filename);
        event.replyFiles(file).setEphemeral(true).queue();
    }

    /** Minimal CSV escaping: wrap in quotes and double internal quotes when needed. */
    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
