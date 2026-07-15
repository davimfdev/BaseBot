package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.leveling.LevelBonus;
import dev.davimf.basebot.modules.base.leveling.LevelFormula;
import dev.davimf.basebot.modules.base.leveling.UserLevelRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.ThreadLocalRandom;

/** Núcleo da economia: ganhos, roubo, transferência, banco. Devolve mensagens já formatadas. */
public final class EconomyService {

    private static final String[] WORK_MSGS = {
            "Você trabalhou como entregador", "Você fez um bico", "Você lavou carros",
            "Você vendeu doces", "Você fez um freela"
    };

    private final BotContext ctx;
    private final WalletRepository wallets;
    private final CooldownRepository cooldowns;

    private final UserLevelRepository userLevels;

    public EconomyService(BotContext ctx) {
        this.ctx = ctx;
        this.wallets = new WalletRepository(ctx.database().sqlite());
        this.cooldowns = new CooldownRepository(ctx.database().sqlite());
        this.userLevels = new UserLevelRepository(ctx.database().sqlite());
    }

    public WalletRepository wallets() { return wallets; }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    private String onCooldown(Guild g, String u, String action, long cooldownS) {
        long readyAt = cooldowns.lastTs(g.getId(), u, action) + cooldownS * 1000L;
        if (System.currentTimeMillis() < readyAt) {
            return "Aguarde — disponível novamente <t:" + (readyAt / 1000) + ":R>.";
        }
        return null;
    }

    public String daily(Guild g, Member m) {
        long now = System.currentTimeMillis();
        long lastTs = cooldowns.lastTs(g.getId(), m.getId(), "daily");
        if (!DailyReset.available(lastTs, now)) {
            long readyAt = DailyReset.nextMidnightMillis(now);
            return "Você já coletou o `/economia daily` hoje — volta <t:" + (readyAt / 1000) + ":R>.";
        }
        int level = LevelFormula.levelForXp(userLevels.xp(g.getId(), m.getId()));
        long amount = LevelBonus.scale(EconomyConfig.daily(cfg(g)), level);
        wallets.addCash(g.getId(), m.getId(), amount);
        cooldowns.stamp(g.getId(), m.getId(), "daily", now);
        return "Recompensa diária: **+" + EconomyFormat.formatNamed(amount, cfg(g)) + "** na carteira.";
    }

    public String work(Guild g, Member m) {
        GuildConfig cfg = cfg(g);
        String cd = onCooldown(g, m.getId(), "work", EconomyConfig.workCooldownSeconds(cfg));
        if (cd != null) {
            return cd;
        }
        long amount = rand(EconomyConfig.workMin(cfg), EconomyConfig.workMax(cfg));
        wallets.addCash(g.getId(), m.getId(), amount);
        cooldowns.stamp(g.getId(), m.getId(), "work", System.currentTimeMillis());
        String flavor = WORK_MSGS[ThreadLocalRandom.current().nextInt(WORK_MSGS.length)];
        return flavor + " e ganhou **+" + EconomyFormat.formatNamed(amount, cfg) + "**.";
    }

    public String pay(Guild g, Member actor, Member target, long amount) {
        if (amount <= 0) {
            return "Informe um valor positivo.";
        }
        if (target.getUser().isBot() || target.getId().equals(actor.getId())) {
            return "Destinatário inválido.";
        }
        if (wallets.transfer(g.getId(), actor.getId(), target.getId(), amount)) {
            return "Você pagou **" + EconomyFormat.formatNamed(amount, cfg(g)) + "** para " + target.getAsMention() + ".";
        }
        return "Saldo insuficiente na carteira.";
    }

    public String deposit(Guild g, Member m, long amount) {
        long cash = wallets.get(g.getId(), m.getId()).cash();
        long amt = amount <= 0 ? cash : Math.min(amount, cash);
        if (amt <= 0 || !wallets.deposit(g.getId(), m.getId(), amt)) {
            return "Nada para depositar.";
        }
        return "Depositado **" + EconomyFormat.formatNamed(amt, cfg(g)) + "** no banco.";
    }

    public String withdraw(Guild g, Member m, long amount) {
        long bank = wallets.get(g.getId(), m.getId()).bank();
        long amt = amount <= 0 ? bank : Math.min(amount, bank);
        if (amt <= 0 || !wallets.withdraw(g.getId(), m.getId(), amt)) {
            return "Nada para sacar.";
        }
        return "Sacado **" + EconomyFormat.formatNamed(amt, cfg(g)) + "** do banco.";
    }

    private static long rand(long min, long max) {
        return max <= min ? min : min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }
}
