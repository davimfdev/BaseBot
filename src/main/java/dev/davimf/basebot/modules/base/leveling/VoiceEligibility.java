package dev.davimf.basebot.modules.base.leveling;

/**
 * Regras puras de elegibilidade em call.
 *
 * <p>Self-mute pausa XP e tempo; server-mute não pausa nada (decisão de produto: quem foi mutado
 * por um moderador não perde progresso). Deafen — self ou de servidor — e o canal AFK pausam
 * ambos. O mínimo de dois humanos vale só para XP: tempo conta sozinho, desde que o canal esteja
 * no escopo configurado.
 */
public final class VoiceEligibility {

    private VoiceEligibility() {}

    public static boolean xpEligible(VoiceStateSnapshot s) {
        return !s.bot() && s.humanCount() >= 2 && !s.deafened() && !s.afkChannel() && !s.selfMuted();
    }

    public static boolean timeEligible(VoiceStateSnapshot s) {
        return !s.bot() && !s.deafened() && !s.afkChannel() && !s.selfMuted() && s.inScope();
    }
}
