package dev.davimf.basebot.modules.base.leveling;

/** Bônus de moedas por nível (integração leveling↔economia). +2%/nível, teto no nível 50. Puro. */
public final class LevelBonus {

    public static final int CAP = 50;
    public static final int PCT_PER_LEVEL = 2;

    private LevelBonus() {}

    public static long scale(long base, int level) {
        int eff = Math.max(0, Math.min(level, CAP));
        return base + base * eff * PCT_PER_LEVEL / 100;
    }
}
