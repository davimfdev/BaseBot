// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.economy
// 
// Class: FinanceService
// 
// Constructors:
//   - `Constructor` : `public FinanceService(BotContext ctx, EconomyRepository economy)`
// 
// Methods:
//   - `Method` : `public Container panel(String guildId)`
//   - `Method` : `private static int pct(GuildConfig cfg, String key)`
//   - `Method` : `private static int clampPct(String raw)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
// 
// Fields:
//   - `Field` : `package-private static final String TOGGLE_LAVAGEM`
//   - `Field` : `package-private static final String TOGGLE_DESMANCHE`
//   - `Field` : `package-private static final String PCT_LAVAGEM`
//   - `Field` : `package-private static final String PCT_DESMANCHE`
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final EconomyRepository economy`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.GuildConfigEdits;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Replies;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import java.util.OptionalLong;

/**
 * Treasury operations behind the {@code /painel-financeiro} panel (BOTSPECS Module 4):
 * deposits, withdrawals, transfers, and the Lavagem/Desmanche toggles + percentages.
 * Every movement logs to {@code #log-financeiro}. Mutating actions require Manage Server.
 */
public final class FinanceService {

    static final String TOGGLE_LAVAGEM = "fin-lavagem";
    static final String TOGGLE_DESMANCHE = "fin-desmanche";
    static final String PCT_LAVAGEM = "fin-lavagem-pct";
    static final String PCT_DESMANCHE = "fin-desmanche-pct";

    private final BotContext ctx;
    private final EconomyRepository economy;

    public FinanceService(BotContext ctx, EconomyRepository economy) {
        this.ctx = ctx;
        this.economy = economy;
    }

    public Container panel(String guildId) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        return FinanceView.panel(EmbedColor.resolve(cfg), economy.getBalance(guildId),
                cfg.toggle(TOGGLE_LAVAGEM, false), pct(cfg, PCT_LAVAGEM),
                cfg.toggle(TOGGLE_DESMANCHE, false), pct(cfg, PCT_DESMANCHE));
    }

    // --- button entry points ---------------------------------------------------

    public void onButton(ButtonInteractionEvent event, String action) {
        if (event.getGuild() == null) {
            return;
        }
        GuildConfig gcfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), gcfg, ManagerPermissions.Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência **Financeiro** pode operar este painel.");
            return;
        }
        String guildId = event.getGuild().getId();
        switch (action) {
            case "deposit" -> event.replyModal(
                    FinanceView.amountModal("depform", "Depósito", "Valor")).queue();
            case "withdraw" -> event.replyModal(
                    FinanceView.amountModal("witform", "Saque", "Valor")).queue();
            case "transfer" -> event.replyModal(FinanceView.transferModal()).queue();
            case "togglelav" -> toggle(event, guildId, TOGGLE_LAVAGEM, "Lavagem");
            case "toggledesm" -> toggle(event, guildId, TOGGLE_DESMANCHE, "Desmanche");
            case "setpct" -> {
                GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
                event.replyModal(FinanceView.pctModal(pct(cfg, PCT_LAVAGEM), pct(cfg, PCT_DESMANCHE))).queue();
            }
            default -> { /* not ours */ }
        }
    }

    public void onModal(ModalInteractionEvent event, String action) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        switch (action) {
            case "depform" -> move(event, guildId, "DEPOSIT", +1, "Depósito");
            case "witform" -> move(event, guildId, "WITHDRAW", -1, "Saque");
            case "traform" -> transfer(event, guildId);
            case "pctform" -> savePct(event, guildId);
            default -> { /* not ours */ }
        }
    }

    // --- operations ------------------------------------------------------------

    private void move(ModalInteractionEvent event, String guildId, String type, int sign, String verb) {
        OptionalLong amount = Money.parse(value(event, "valor"));
        if (amount.isEmpty() || amount.getAsLong() <= 0) {
            Replies.ephemeral(event, ctx, "Valor inválido.");
            return;
        }
        long delta = sign * amount.getAsLong();
        long balance = economy.adjust(guildId, type, delta, event.getUser().getId(), verb);
        log(guildId, "## " + Emojis.of(Emojis.MONEY, "💰") + " " + verb + "\n" + Emojis.of(Emojis.CASH, "💵") + " **Valor** · `" + Money.format(amount.getAsLong()) + "`"
                + "\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Por** · <@" + event.getUser().getId() + ">\n" + Emojis.of(Emojis.BANK, "🏦") + " **Novo saldo** · `"
                + Money.format(balance) + "`");
        refresh(event, guildId);
    }

    private void transfer(ModalInteractionEvent event, String guildId) {
        OptionalLong amount = Money.parse(value(event, "valor"));
        ModalMapping destino = event.getValue("destino");
        Mentions mentions = destino == null ? null : destino.getAsMentions();
        User target = mentions == null || mentions.getUsers().isEmpty() ? null : mentions.getUsers().get(0);
        if (amount.isEmpty() || amount.getAsLong() <= 0 || target == null) {
            Replies.ephemeral(event, ctx, "Valor ou destinatário inválido.");
            return;
        }
        long balance = economy.adjust(guildId, "TRANSFER", -amount.getAsLong(),
                event.getUser().getId(), "Transferência para " + target.getId());
        log(guildId, "## " + Emojis.of(Emojis.EXPENSE, "💸") + " Transferência\n" + Emojis.of(Emojis.CASH, "💵") + " **Valor** · `" + Money.format(amount.getAsLong()) + "`"
                + "\n" + Emojis.of(Emojis.TARGET, "🎯") + " **Para** · <@" + target.getId() + ">\n" + Emojis.of(Emojis.MEMBER, "👤") + " **Por** · <@" + event.getUser().getId()
                + ">\n" + Emojis.of(Emojis.BANK, "🏦") + " **Novo saldo** · `" + Money.format(balance) + "`");
        refresh(event, guildId);
    }

    private void toggle(ButtonInteractionEvent event, String guildId, String key, String label) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        boolean now = !cfg.toggle(key, false);
        ctx.database().guildConfig().save(GuildConfigEdits.withToggle(cfg, key, now));
        log(guildId, "## " + Emojis.of(Emojis.GEAR, "⚙️") + " " + label + " " + (now ? "ativada" : "desativada")
                + " por <@" + event.getUser().getId() + ">.");
        refresh(event, guildId);
    }

    private void savePct(ModalInteractionEvent event, String guildId) {
        int lav = clampPct(value(event, "lavagem"));
        int desm = clampPct(value(event, "desmanche"));
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        cfg = GuildConfigEdits.withSetting(cfg, PCT_LAVAGEM, String.valueOf(lav));
        cfg = GuildConfigEdits.withSetting(cfg, PCT_DESMANCHE, String.valueOf(desm));
        ctx.database().guildConfig().save(cfg);
        refresh(event, guildId);
    }

    // --- helpers ---------------------------------------------------------------

    private void refresh(IMessageEditCallback event, String guildId) {
        event.editComponents(panel(guildId)).useComponentsV2().queue();
    }

    private void log(String guildId, String markdown) {
        FacsLog.post(ctx, guildId, "log-financeiro", markdown);
    }

    private static int pct(GuildConfig cfg, String key) {
        return clampPct(cfg.setting(key));
    }

    private static int clampPct(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(raw.trim().replaceAll("[^0-9]", ""))));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }
}
