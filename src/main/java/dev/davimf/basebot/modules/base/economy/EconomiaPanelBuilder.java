package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomiaPanelView.ActionLine;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Monta o Container do painel /economia (usado pelo comando e pelo handler do toggle). */
public final class EconomiaPanelBuilder {

    private EconomiaPanelBuilder() {}

    public static Container build(BotContext ctx, Guild g, Member m, JailService jail, JobNotifyRepository notify) {
        String gid = g.getId();
        String uid = m.getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(gid);
        long now = System.currentTimeMillis();
        var wallets = new WalletRepository(ctx.database().sqlite());
        var cooldowns = new CooldownRepository(ctx.database().sqlite());
        var inv = new InventoryRepository(ctx.database().sqlite());

        WalletRepository.Wallet w = wallets.get(gid, uid);
        JailService.Status st = jail.resolve(gid, uid);
        boolean preso = st.kind() == JailService.Kind.PRESO;
        boolean ficha = jail.fichaSuja(gid, uid);

        Map<Slot, Row> equipped = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            Row r = inv.equipped(gid, uid, s);
            if (r != null) {
                equipped.put(s, r);
            }
        }

        List<ActionLine> acoes = new ArrayList<>();
        acoes.add(dailyLine(gid, uid, now, cooldowns, preso));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_WORK, "economia trabalhar",
                EconomyConfig.workCooldownSeconds(cfg), null, false, equipped, null));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_MINE, "economia minerar",
                EconomyDefaults.MINE_COOLDOWN_S, Slot.MINING, false, equipped, "equipe uma picareta no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_COOK, "economia cozinhar",
                EconomyDefaults.COOK_COOLDOWN_S, Slot.COOKING, false, equipped, "equipe um utensílio no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_DELIVERY, "economia entregar",
                EconomyDefaults.DELIVERY_COOLDOWN_S, Slot.DELIVERY, false, equipped, "equipe uma moto no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_PROGRAM, "economia programar",
                EconomyDefaults.PROGRAM_COOLDOWN_S, Slot.TECH, false, equipped, "equipe um teclado no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_PLANT, "economia plantar",
                EconomyDefaults.PLANT_COOLDOWN_S, Slot.FARM, false, equipped, "equipe um equipamento de fazenda no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_FISH, "economia pescar",
                EconomyDefaults.FISH_COOLDOWN_S, Slot.FISHING, false, equipped, "equipe uma vara no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_EXPLORE, "economia explorar",
                EconomyDefaults.EXPLORE_COOLDOWN_S, Slot.EXPEDITION, false, equipped, "equipe um equipamento de expedição no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_INVOICE, "economia faturar",
                EconomyDefaults.INVOICE_COOLDOWN_S, Slot.BUSINESS, false, equipped, "equipe um contrato no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_CRIME, "economia crime",
                EconomyDefaults.CRIME_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_ROB, "economia roubar",
                EconomyDefaults.ROB_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/economia mercado`"));
        acoes.add(line(gid, uid, now, cooldowns, preso, EconomyCooldownKeys.CD_ORG, "economia crimeorganizado",
                EconomyDefaults.ORG_DAILY_COOLDOWN_S, Slot.WEAPON, false, equipped, "equipe uma arma no `/economia mercado`"));

        boolean notifyOn = notify.isEnabled(gid, uid);
        return EconomiaPanelView.panel(EmbedColor.resolve(cfg), m, w, st, ficha, acoes, notifyOn, cfg);
    }

    /** Linha do daily: reset por meia-noite (isento de cadeia). */
    private static ActionLine dailyLine(String g, String u, long now, CooldownRepository cd, boolean preso) {
        long lastTs = cd.lastTs(g, u, EconomyCooldownKeys.CD_DAILY);
        ActionStatus status = DailyReset.available(lastTs, now)
                ? new ActionStatus(ActionStatus.Kind.READY, 0)
                : new ActionStatus(ActionStatus.Kind.COOLDOWN, DailyReset.nextMidnightMillis(now));
        return new ActionLine("economia daily", status, null, null);
    }

    private static ActionLine line(String g, String u, long now, CooldownRepository cd, boolean preso, String key,
                                   String command, long cooldownS, Slot slot, boolean exempt, Map<Slot, Row> equipped, String hint) {
        boolean unlocked = slot == null || equipped.containsKey(slot);
        ActionStatus status = ActionStatus.resolve(now, cd.lastTs(g, u, key), cooldownS, unlocked, preso, exempt);
        String eqLabel = null;
        if (slot != null && equipped.containsKey(slot)) {
            Row r = equipped.get(slot);
            Equip e = EquipmentCatalog.byKey(r.itemKey());
            eqLabel = (e == null ? "Item desconhecido" : e.name()) + " " + r.usosLeft() + "/"
                    + (e == null ? "?" : String.valueOf(e.maxUsos()));
        }
        return new ActionLine(command, status, hint, eqLabel);
    }
}
