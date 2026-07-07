package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/** Cadeia + ficha: resolução lazy (cumprir pena marca ficha), guard, fiança e limpar-ficha. */
public final class JailService {

    public enum Kind { LIVRE, PRESO }
    public record Status(Kind kind, long presoAte) {}

    private final BotContext ctx;
    private final CrimeStateRepository states;
    private final WalletRepository wallets;

    public JailService(BotContext ctx) {
        this.ctx = ctx;
        this.states = new CrimeStateRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }

    /** Helper testável: se preso e a pena passou, marca ficha + libera; devolve true se cumpriu a pena agora. */
    static boolean resolveServed(CrimeStateRepository states, String g, String u, long now) {
        CrimeStateRepository.State s = states.get(g, u);
        if (s.presoAte() > 0 && now >= s.presoAte()) {
            states.setFicha(g, u, true);
            states.release(g, u);
            return true;
        }
        return false;
    }

    public Status resolve(String g, String u) {
        long now = System.currentTimeMillis();
        resolveServed(states, g, u, now); // marca ficha + libera se cumpriu a pena
        CrimeStateRepository.State s = states.get(g, u);
        return s.presoAte() > now ? new Status(Kind.PRESO, s.presoAte()) : new Status(Kind.LIVRE, 0);
    }

    public boolean fichaSuja(String g, String u) { return states.get(g, u).fichaSuja(); }

    public void jailFor(String g, String u, long millis) {
        states.jail(g, u, System.currentTimeMillis() + millis);
    }

    /** true se preso: responde efêmero e o comando deve abortar. */
    public boolean blockedIfJailed(IReplyCallback event, BotContext ctx, String g, String u) {
        Status st = resolve(g, u);
        if (st.kind() == Kind.PRESO) {
            dev.davimf.basebot.util.Replies.ephemeral(event, ctx,
                    "Você está preso — sai <t:" + (st.presoAte() / 1000) + ":R>. Pague `/fianca` pra sair agora.");
            return true;
        }
        return false;
    }

    /** Paga a fiança: libera sem marcar a ficha. Devolve mensagem. */
    public String bail(String g, String u) {
        if (resolve(g, u).kind() != Kind.PRESO) {
            return "Você não está preso.";
        }
        if (!wallets.tryDebitCash(g, u, EconomyDefaults.BAIL_BASE)) {
            return "Saldo insuficiente na carteira pra fiança (precisa de " + EconomyDefaults.BAIL_BASE + ").";
        }
        states.release(g, u); // não marca ficha
        return "Fiança paga. Você está livre — a prisão não foi pra sua ficha.";
    }

    /** Limpa a ficha suja (custa EXPUNGE). */
    public String expunge(String g, String u) {
        if (resolve(g, u).kind() == Kind.PRESO) {
            return "Você está preso — resolva isso primeiro.";
        }
        if (!states.get(g, u).fichaSuja()) {
            return "Sua ficha já está limpa.";
        }
        if (!wallets.tryDebitCash(g, u, EconomyDefaults.EXPUNGE)) {
            return "Saldo insuficiente pra limpar a ficha (precisa de " + EconomyDefaults.EXPUNGE + ").";
        }
        states.setFicha(g, u, false);
        return "Ficha limpa.";
    }
}
