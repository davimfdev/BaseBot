package dev.davimf.basebot.modules.base.leveling;

/** Regra pura de elegibilidade de XP por voz. Conta HUMANOS (não membros — filtrar bots antes). */
public final class VoiceEligibility {

    private VoiceEligibility() {}

    public static boolean isEligible(boolean isBot, long humanCount, boolean deafened, boolean afkChannel) {
        return !isBot && humanCount >= 2 && !deafened && !afkChannel;
    }
}
