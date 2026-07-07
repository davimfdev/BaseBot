package dev.davimf.basebot.modules.base.leveling;

/** Curva de XP estilo MEE6. Puro/testável. XP p/ subir do nível n: 5n² + 50n + 100. */
public final class LevelFormula {

    private LevelFormula() {}

    public record Progress(int level, long into, long needed) {}

    public static long xpForLevel(int level) {
        long n = level;
        return 5 * n * n + 50 * n + 100;
    }

    public static long totalXpForLevel(int level) {
        long sum = 0;
        for (int n = 0; n < level; n++) {
            sum += xpForLevel(n);
        }
        return sum;
    }

    public static int levelForXp(long totalXp) {
        int level = 0;
        long acc = 0;
        while (acc + xpForLevel(level) <= totalXp) {
            acc += xpForLevel(level);
            level++;
        }
        return level;
    }

    public static Progress progress(long totalXp) {
        int level = levelForXp(totalXp);
        long base = totalXpForLevel(level);
        return new Progress(level, totalXp - base, xpForLevel(level));
    }
}
