package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResult;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.vip.VipBonus;
import dev.davimf.basebot.modules.base.vip.VipBonusSource;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** Empregos: cooldown → equipado → useOnce (confirma) → paga. Devolve mensagem formatada. */
public final class JobService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;
    private final VipBonusSource vip;

    public JobService(BotContext ctx, VipBonusSource vip) {
        this.ctx = ctx;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
        this.vip = vip;
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String onCooldown(Guild g, String u, String action, long cooldownS) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + cooldownS * 1000L;
        return System.currentTimeMillis() < readyAt
                ? "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>." : null;
    }

    public String runMining(Guild g, Member m) { return runTool(g, m, Slot.MINING, "minerar",
            EconomyDefaults.MINE_COOLDOWN_S, "picareta", Emojis.of(Emojis.GEM, "⛏️")); }

    public String runCooking(Guild g, Member m) { return runTool(g, m, Slot.COOKING, "cozinhar",
            EconomyDefaults.COOK_COOLDOWN_S, "utensílio de cozinha", Emojis.of(Emojis.GEAR, "🍳")); }

    public String runProgram(Guild g, Member m) { return runTool(g, m, Slot.TECH, "programar",
            EconomyDefaults.PROGRAM_COOLDOWN_S, "teclado", Emojis.of(Emojis.GEAR, "💻")); }

    public String runPlant(Guild g, Member m) { return runTool(g, m, Slot.FARM, "plantar",
            EconomyDefaults.PLANT_COOLDOWN_S, "equipamento de fazenda", Emojis.of(Emojis.SPROUT, "🌱")); }

    public String runFish(Guild g, Member m) { return runTool(g, m, Slot.FISHING, "pescar",
            EconomyDefaults.FISH_COOLDOWN_S, "vara de pesca", Emojis.of(Emojis.PRODUCT, "🎣")); }

    public String runExplore(Guild g, Member m) { return runTool(g, m, Slot.EXPEDITION, "explorar",
            EconomyDefaults.EXPLORE_COOLDOWN_S, "equipamento de expedição", Emojis.of(Emojis.COMPASS, "🧭")); }

    public String runInvoice(Guild g, Member m) { return runTool(g, m, Slot.BUSINESS, "faturar",
            EconomyDefaults.INVOICE_COOLDOWN_S, "contrato/licença", Emojis.of(Emojis.MONEY, "💼")); }

    private String runTool(Guild g, Member m, Slot slot, String action, long cd, String noun, String emoji) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = onCooldown(g, u, action, cd);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row row = inv.equipped(g.getId(), u, slot);
        if (row == null) {
            return "Equipe " + (slot == Slot.MINING ? "uma " : "um ") + noun + " no `/economia inventario` primeiro.";
        }
        Equip e = EquipmentCatalog.byKey(row.itemKey());
        UseResult use = inv.useOnce(g.getId(), u, row.id());
        if (use.type() == UseResultType.NOT_FOUND || use.type() == UseResultType.NOT_OWNER) {
            return "Seu " + noun + " não está mais disponível — equipe de novo.";
        }
        long amount = JobOutcome.reward(e.payoutMin(), e.payoutMax(), ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        amount = VipBonus.scale(amount, vip.bonusFor(g.getId(), u).ecoPct());
        wallets.addCash(g.getId(), u, amount);
        cooldowns.stamp(g.getId(), u, action, System.currentTimeMillis());
        String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Seu " + noun + " quebrou." : "";
        return emoji + " " + e.name() + " rendeu **+" + EconomyFormat.formatNamed(amount, cfg) + "**." + broke;
    }

    public String runDelivery(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = onCooldown(g, u, "entregar", EconomyDefaults.DELIVERY_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row row = inv.equipped(g.getId(), u, Slot.DELIVERY);
        if (row == null) {
            return "Equipe uma moto no `/economia inventario` primeiro.";
        }
        Equip e = EquipmentCatalog.byKey(row.itemKey());
        if (!wallets.tryDebitCash(g.getId(), u, e.fuel())) { // debita o combustível atomicamente (anti read-then-act)
            return "Sem dinheiro pro combustível (precisa de " + EconomyFormat.formatNamed(e.fuel(), cfg) + ").";
        }
        UseResult use = inv.useOnce(g.getId(), u, row.id());
        if (use.type() == UseResultType.NOT_FOUND || use.type() == UseResultType.NOT_OWNER) {
            wallets.addCash(g.getId(), u, e.fuel()); // moto sumiu numa corrida → estorna o combustível
            return "Sua moto não está mais disponível — equipe de novo.";
        }
        long gross = JobOutcome.reward(e.payoutMin(), e.payoutMax(), ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
        gross = VipBonus.scale(gross, vip.bonusFor(g.getId(), u).ecoPct());
        wallets.addCash(g.getId(), u, gross);
        cooldowns.stamp(g.getId(), u, "entregar", System.currentTimeMillis());
        String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua moto quebrou." : "";
        return Emojis.of(Emojis.COMPASS, "🏍️") + " Entrega feita: **+" + EconomyFormat.formatNamed(gross, cfg)
                + "** (−" + EconomyFormat.formatNamed(e.fuel(), cfg) + " de combustível)." + broke;
    }
}
