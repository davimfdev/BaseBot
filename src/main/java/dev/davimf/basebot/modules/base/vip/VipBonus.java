package dev.davimf.basebot.modules.base.vip;

/** Multiplicador de bônus VIP. Puro. Arredonda para baixo (floorDiv). */
public final class VipBonus {
    private VipBonus() {}

    public static long scale(long base, int pct) {
        int p = Math.max(0, pct);
        return base + Math.floorDiv(base * p, 100);
    }

    public static int cap(int pct, int max) {
        return Math.max(0, Math.min(pct, max));
    }
}
