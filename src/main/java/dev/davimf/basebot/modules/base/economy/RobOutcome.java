package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /roubar. Chance = clamp(base+bonus−fichaPenalty, 5, 95); stolen capado por robCap e floor. */
public record RobOutcome(boolean success, long stolen, long fine) {
    public static RobOutcome resolve(int roll, int base, int bonus, int fichaPenalty, int stealPct,
                                     long targetCash, double mult, long robCap, long protectedFloor,
                                     long fineRolled, long attackerCash) {
        int chance = Math.max(5, Math.min(95, base + bonus - fichaPenalty));
        long stealable = Math.max(0, targetCash - protectedFloor);
        if (roll < chance) {
            long raw = Math.round(targetCash * stealPct / 100.0 * mult);
            long stolen = Math.max(0, Math.min(Math.min(raw, robCap), stealable));
            return new RobOutcome(true, stolen, 0);
        }
        return new RobOutcome(false, 0, Math.min(fineRolled, Math.max(0, attackerCash)));
    }
}
