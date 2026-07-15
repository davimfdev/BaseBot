package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResult;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.UseResultType;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.DestroyResultType;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** /crime e /roubar armados: arma obrigatória, consumo/destruição antes de pagar, ficha penaliza. */
public final class CrimeEconomyService {

    private final BotContext ctx;
    private final InventoryRepository inv;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;
    private final JailService jail;

    public CrimeEconomyService(BotContext ctx, JailService jail) {
        this.ctx = ctx;
        this.jail = jail;
        this.inv = new InventoryRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
    }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String cd(Guild g, String u, String action, long s) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + s * 1000L;
        return System.currentTimeMillis() < readyAt
                ? "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>." : null;
    }

    private static int rnd(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    private static long rnd(long min, long max) {
        return max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }

    public String runCrime(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String u = m.getId();
        String cdMsg = cd(g, u, "crime", EconomyDefaults.CRIME_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        Row weapon = inv.equipped(g.getId(), u, Slot.WEAPON);
        if (weapon == null) {
            return "Equipe uma arma no `/inventario` pra cometer crimes.";
        }
        Equip w = EquipmentCatalog.byKey(weapon.itemKey());
        int penalty = jail.fichaSuja(g.getId(), u) ? EconomyDefaults.FICHA_PENALTY_PCT : 0;
        long cash = wallets.get(g.getId(), u).cash();
        CrimeOutcome o = CrimeOutcome.resolve(rnd(100), EconomyDefaults.CRIME_SUCCESS_PCT, w.chanceBonus(), penalty,
                rnd(EconomyDefaults.CRIME_WIN_MIN, EconomyDefaults.CRIME_WIN_MAX), w.mult(),
                rnd(EconomyDefaults.CRIME_FINE_MIN, EconomyDefaults.CRIME_FINE_MAX), cash);
        if (o.success()) {
            UseResult use = inv.useOnce(g.getId(), u, weapon.id());
            if (use.type() != UseResultType.USED && use.type() != UseResultType.USED_AND_BROKE) {
                return "Sua arma não está mais disponível — equipe de novo."; // NOT_FOUND/NOT_OWNER/PERMANENT
            }
            wallets.addCash(g.getId(), u, o.gain());
            cooldowns.stamp(g.getId(), u, "crime", System.currentTimeMillis());
            String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua " + w.name() + " quebrou." : "";
            return Emojis.of(Emojis.SKULL, "🔫") + " Crime bem-sucedido! **+" + EconomyFormat.formatNamed(o.gain(), cfg) + "**." + broke;
        }
        // Falha: destrói a arma ANTES da multa.
        if (inv.destroy(g.getId(), u, weapon.id()).type() != DestroyResultType.DESTROYED) {
            return "Sua arma não está mais disponível — equipe de novo.";
        }
        wallets.tryDebitCash(g.getId(), u, o.fine());
        cooldowns.stamp(g.getId(), u, "crime", System.currentTimeMillis());
        return Emojis.of(Emojis.KICK, "🚔") + " Você foi pego! Perdeu a **" + w.name() + "** e pagou **"
                + EconomyFormat.formatNamed(o.fine(), cfg) + "** de multa.";
    }

    public String runRobbery(Guild g, Member actor, Member target) {
        if (target.getUser().isBot() || target.getId().equals(actor.getId())) {
            return "Alvo inválido.";
        }
        GuildConfig cfg = cfg(g);
        String u = actor.getId();
        String cdMsg = cd(g, u, "rob", EconomyDefaults.ROB_COOLDOWN_S);
        if (cdMsg != null) {
            return cdMsg;
        }
        String pairCd = cd(g, u, "rob:" + target.getId(), EconomyDefaults.ROB_PAIR_COOLDOWN_S);
        if (pairCd != null) {
            return "Você roubou " + target.getEffectiveName() + " há pouco — espere um tempo.";
        }
        Row weapon = inv.equipped(g.getId(), u, Slot.WEAPON);
        if (weapon == null) {
            return "Equipe uma arma no `/inventario` pra roubar.";
        }
        long targetCash = wallets.get(g.getId(), target.getId()).cash();
        if (targetCash - EconomyDefaults.ROB_PROTECTED_FLOOR <= 0) {
            return target.getEffectiveName() + " não tem dinheiro suficiente pra roubar.";
        }
        Equip w = EquipmentCatalog.byKey(weapon.itemKey());
        int penalty = jail.fichaSuja(g.getId(), u) ? EconomyDefaults.FICHA_PENALTY_PCT : 0;
        int stealPct = (int) rnd(EconomyDefaults.ROB_STEAL_MIN_PCT, EconomyDefaults.ROB_STEAL_MAX_PCT);
        long actorCash = wallets.get(g.getId(), u).cash();
        RobOutcome o = RobOutcome.resolve(rnd(100), EconomyDefaults.ROB_SUCCESS_PCT, w.chanceBonus(), penalty,
                stealPct, targetCash, w.mult(), w.robCap(), EconomyDefaults.ROB_PROTECTED_FLOOR,
                rnd(EconomyDefaults.ROB_FINE_MIN, EconomyDefaults.ROB_FINE_MAX), actorCash);
        long now = System.currentTimeMillis();
        if (o.success()) {
            UseResult use = inv.useOnce(g.getId(), u, weapon.id());
            if (use.type() != UseResultType.USED && use.type() != UseResultType.USED_AND_BROKE) {
                return "Sua arma não está mais disponível — equipe de novo."; // NOT_FOUND/NOT_OWNER/PERMANENT
            }
            boolean moved = wallets.transfer(g.getId(), target.getId(), u, o.stolen());
            cooldowns.stamp(g.getId(), u, "rob", now);
            cooldowns.stamp(g.getId(), u, "rob:" + target.getId(), now);
            String broke = use.type() == UseResultType.USED_AND_BROKE ? "\n-# Sua " + w.name() + " quebrou." : "";
            if (!moved) { // corrida: o alvo esvaziou a carteira entre o cálculo e a transferência
                return "O roubo falhou — o alvo não tinha mais o valor na hora. Sua **" + w.name() + "** foi usada." + broke;
            }
            return Emojis.of(Emojis.SKULL, "🕵️") + " Você roubou **" + EconomyFormat.formatNamed(o.stolen(), cfg)
                    + "** de " + target.getAsMention() + "!" + broke;
        }
        // Falha: a arma desgasta (−4 usos) ANTES da multa (paga ao alvo).
        UseResult wear = inv.useMany(g.getId(), u, weapon.id(), 4);
        if (wear.type() != UseResultType.USED && wear.type() != UseResultType.USED_AND_BROKE) {
            return "Sua arma não está mais disponível — equipe de novo."; // NOT_FOUND/NOT_OWNER/PERMANENT
        }
        if (o.fine() > 0) {
            wallets.transfer(g.getId(), u, target.getId(), o.fine());
        }
        cooldowns.stamp(g.getId(), u, "rob", now);
        cooldowns.stamp(g.getId(), u, "rob:" + target.getId(), now);
        String worn = wear.type() == UseResultType.USED_AND_BROKE
                ? "se desgastou e quebrou" : "se desgastou (−4 usos)";
        return Emojis.of(Emojis.KICK, "🚔") + " Roubo fracassado! Sua **" + w.name() + "** " + worn
                + " e você pagou **" + EconomyFormat.formatNamed(o.fine(), cfg) + "** a " + target.getAsMention() + ".";
    }
}
