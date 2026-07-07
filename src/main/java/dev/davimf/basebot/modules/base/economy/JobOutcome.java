package dev.davimf.basebot.modules.base.economy;

/** Resolver puro do lucro de um emprego dado um roll (determinístico → testável). */
public final class JobOutcome {
    private JobOutcome() {}

    /** Valor em [min, max]; {@code roll} pode ser qualquer inteiro não-negativo. */
    public static long reward(long min, long max, long roll) {
        if (max <= min) {
            return min;
        }
        long span = max - min + 1;
        return min + Math.floorMod(roll, span);
    }
}
