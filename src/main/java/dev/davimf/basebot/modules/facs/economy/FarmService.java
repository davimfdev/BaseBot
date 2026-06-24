package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Money;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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

    public void submit(SlashCommandInteractionEvent event, String item, long quantity) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String channelId = cfg.channel("log-farm");
        TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
        if (channel == null) {
            event.reply("Canal de farm não configurado em /setup → Logs.").setEphemeral(true).queue();
            return;
        }
        String pendingId = farm.create(guildId, event.getUser().getId(), item, quantity);
        channel.sendMessageComponents(FarmView.request(EmbedColor.resolve(cfg), pendingId,
                        event.getUser().getId(), item, quantity))
                .useComponentsV2()
                .setAllowedMentions(List.of())
                .queue(msg -> event.reply("🌾 Entrega enviada para aprovação.").setEphemeral(true).queue(),
                        err -> {
                            farm.delete(pendingId);
                            event.reply("Falha ao enviar: " + err.getMessage()).setEphemeral(true).queue();
                        });
    }

    public void approve(ButtonInteractionEvent event, String pendingId) {
        if (!managerGate(event)) {
            return;
        }
        FarmRepository.Pending p = farm.find(pendingId).orElse(null);
        if (p == null) {
            event.reply("Esta entrega já foi processada.").setEphemeral(true).queue();
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

        event.editComponents(FarmView.resolved(accent, p.farmerId(), p.item(), p.quantity(),
                "✅ Aprovado por <@" + event.getUser().getId() + ">. Estoque: " + newStock))
                .useComponentsV2().queue();
        StringBuilder log = new StringBuilder("## 🌾 Farm aprovado\n")
                .append("<@").append(p.farmerId()).append("> entregou ").append(p.quantity())
                .append("x **").append(p.item()).append("** · estoque agora ").append(newStock);
        if (payout > 0) {
            log.append("\n💸 Pagamento: ").append(Money.format(payout))
                    .append(" · saldo: ").append(Money.format(balance));
        }
        FacsLog.post(ctx, guildId, "log-farm", log.toString());
        ctx.database().actionLogs().log(guildId, event.getUser().getId(), p.farmerId(),
                "FARM_APPROVE", p.quantity() + "x " + p.item());
    }

    public void reject(ButtonInteractionEvent event, String pendingId) {
        if (!managerGate(event)) {
            return;
        }
        FarmRepository.Pending p = farm.find(pendingId).orElse(null);
        if (p == null) {
            event.reply("Esta entrega já foi processada.").setEphemeral(true).queue();
            return;
        }
        farm.delete(pendingId);
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(p.guildId()));
        event.editComponents(FarmView.resolved(accent, p.farmerId(), p.item(), p.quantity(),
                "❌ Recusado por <@" + event.getUser().getId() + ">.")).useComponentsV2().queue();
    }

    private boolean managerGate(ButtonInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Apenas a gerência pode aprovar/recusar entregas de farm.")
                    .setEphemeral(true).queue();
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
