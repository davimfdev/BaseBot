package dev.davimf.basebot.modules.base.economy;

/** Resultado puro de /crime. Chance = clamp(base+bonus−fichaPenalty, 5, 95). */
public record CrimeOutcome(boolean success, long gain, long fine) {
    public static CrimeOutcome resolve(int roll, int base, int bonus, int fichaPenalty,
                                       long winRolled, double mult, long fineRolled, long cash) {
        int chance = Math.max(5, Math.min(95, base + bonus - fichaPenalty));
        if (roll < chance) {
            return new CrimeOutcome(true, Math.round(winRolled * mult), 0);
        }
        return new CrimeOutcome(false, 0, Math.min(fineRolled, Math.max(0, cash)));
    }
}
