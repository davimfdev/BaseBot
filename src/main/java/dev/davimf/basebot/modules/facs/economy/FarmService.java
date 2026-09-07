package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

/**
 * Farm submission flow (BOTSPECS Module 4): a member submits raw materials, a manager
 * approves, and approval adds to {@code fac_stock} and pays the farmer from the treasury
 * (per-unit rate in guild_config.settings {@code farm-payout-cents}, default 0).
 */
public final class FarmService {

    /** guild_config.settings key: how much the treasury pays per farmed unit (cents). */
    static final String PAYOUT_KEY = "farm-payout-cents";

    private final BotContext ctx;
    private final EconomyRepository economy;
    private final FarmRepository farm;

    public FarmService(BotContext ctx, EconomyRepository economy, FarmRepository farm) {
        this.ctx = ctx;
        this.economy = economy;
        this.farm = farm;
    }

    public void submit(ModalInteractionEvent event, String item, long quantity) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String channelId = cfg.channel("log-farm");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            Replies.ephemeral(event, ctx, "Canal de farm não configurado em /setup → Logs.");
            return;
        }
        String pendingId = farm.create(guildId, event.getUser().getId(), item, quantity);
        channel.sendMessageComponents(FarmView.request(EmbedColor.resolve(cfg), pendingId,
                        event.getUser().getId(), item, quantity))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.FARM, "🌾") + " Entrega enviada para aprovação."),
                        err -> {
                            farm.delete(pendingId);
                            Replies.ephemeral(event, ctx, "Falha ao enviar: " + err.getMessage());
                        });
    }

    public void approve(ButtonInteractionEvent event, String pendingId) {
        if (!managerGate(event)) {
            return;
        }
        FarmRepository.Pending p = farm.find(pendingId).orElse(null);
        if (p == null) {
            Replies.ephemeral(event, ctx, "Esta entrega já foi processada.");
            return;
        }
        String guildId = p.guildId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        int accent = EmbedColor.resolve(cfg);

        long newStock = economy.addStock(guildId, p.item(), p.quantity());
        long payout = payoutRate(cfg) * p.quantity();
        long balance = -1;
        if (payout > 0) {
            balance = economy.adjust(guildId, "FARM_PAYOUT", -payout, event.getUser().getId(),
                    "Farm " + p.quantity() + "x " + p.item() + " de " + p.farmerId());
        }
        farm.delete(pendingId);

        // The request message lives in #log-farm; edit it into the final record (no extra log).
        String status = "" + Emojis.of(Emojis.CHECK_YES, "✅") + " **Aprovado** por <@" + event.getUser().getId() + ">\n"
                + "" + Emojis.of(Emojis.STATS, "📊") + " **Estoque** · `" + newStock + "`"
                + (payout > 0 ? "\n" + Emojis.of(Emojis.EXPENSE, "💸") + " **Pagamento** · `" + Money.format(payout)
                        + "` · saldo `" + Money.format(balance) + "`" : "");
        event.editComponents(FarmView.resolved(accent, p.farmerId(), p.item(), p.quantity(), status))
                .useComponentsV2().queue();
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), p.farmerId(),
                "FARM_APPROVE", p.quantity() + "x " + p.item());
    }

    public void reject(ButtonInteractionEvent event, String pendingId) {
        if (!managerGate(event)) {
            return;
        }
        FarmRepository.Pending p = farm.find(pendingId).orElse(null);
        if (p == null) {
            Replies.ephemeral(event, ctx, "Esta entrega já foi processada.");
            return;
        }
        farm.delete(pendingId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(p.guildId()));
        event.editComponents(FarmView.resolved(accent, p.farmerId(), p.item(), p.quantity(),
                "" + Emojis.of(Emojis.CHECK_NO, "❌") + " Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
    }

    private boolean managerGate(ButtonInteractionEvent event) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.FARM)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Farm** pode aprovar/recusar entregas.");
            return false;
        }
        return true;
    }

    private static long payoutRate(GuildConfig cfg) {
        String raw = cfg.setting(PAYOUT_KEY);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
