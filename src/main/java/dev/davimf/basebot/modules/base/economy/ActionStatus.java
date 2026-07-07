package dev.davimf.basebot.modules.base.economy;

/** Estado puro de uma ação da economia. readyAt só é significativo em COOLDOWN (senão 0). */
public record ActionStatus(Kind kind, long readyAt) {

    public enum Kind { READY, COOLDOWN, LOCKED, JAILED }

    /** Precedência: preso (não isento) → bloqueado (sem equipamento) → cooldown → pronto. */
    public static ActionStatus resolve(long now, long lastTs, long cooldownS,
                                       boolean unlocked, boolean jailed, boolean exemptWhenJailed) {
        if (jailed && !exemptWhenJailed) {
            return new ActionStatus(Kind.JAILED, 0);
        }
        if (!unlocked) {
            return new ActionStatus(Kind.LOCKED, 0);
        }
        long readyAt = lastTs + cooldownS * 1000L;
        if (now < readyAt) {
            return new ActionStatus(Kind.COOLDOWN, readyAt);
        }
        return new ActionStatus(Kind.READY, 0);
    }
}
