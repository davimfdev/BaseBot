package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.util.Emojis;

import java.util.List;

/** Casos de uso de equipamento: comprar (revalida catálogo/preço/saldo) e equipar. */
public final class EquipmentService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;

    public EquipmentService(BotContext ctx) {
        this.ctx = ctx;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }

    public InventoryRepository inv() { return inv; }

    private GuildConfig cfg(String g) { return ctx.database().guildConfig().findOrEmpty(g); }

    public String buy(String g, String u, String itemKey) {
        GuildConfig cfg = cfg(g);
        if (!EconomyConfig.enabled(cfg)) {
            return "Economia desativada.";
        }
        Equip e = EquipmentCatalog.byKey(itemKey); // revalida no clique (não confia na UI)
        if (e == null) {
            return "Item indisponível.";
        }
        if (!wallets.tryDebitCash(g, u, e.price())) {
            return "Saldo insuficiente na carteira (" + EconomyFormat.formatNamed(e.price(), cfg) + ").";
        }
        inv.buy(g, u, itemKey);
        return Emojis.of(Emojis.CHECK_YES, "✅") + " Comprou **" + e.name() + "** (−"
                + EconomyFormat.formatNamed(e.price(), cfg) + "). Equipe no `/inventario`.";
    }

    public String equip(String g, String u, long rowId) {
        return inv.equip(g, u, rowId)
                ? Emojis.of(Emojis.CHECK_YES, "✅") + " Equipado."
                : "Não consegui equipar (item inexistente ou não é seu).";
    }

    public List<InventoryRepository.Row> inventory(String g, String u) { return inv.list(g, u); }

    /** Rótulo curto de um item pra UI (usa o catálogo). */
    public static String label(Equip e) {
        String extra = switch (e.slot()) {
            case WEAPON -> "+" + e.chanceBonus() + "% · " + e.mult() + "x";
            case DELIVERY -> "fuel " + e.fuel();
            default -> e.payoutMin() + "–" + e.payoutMax();
        };
        return e.name() + " · " + extra + " · " + e.maxUsos() + " usos";
    }
}
