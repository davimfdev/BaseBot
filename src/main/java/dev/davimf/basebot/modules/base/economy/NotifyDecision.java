package dev.davimf.basebot.modules.base.economy;

/** Decisão pura de avisar um trabalho disponível (sem JDA/DB). */
public final class NotifyDecision {

    private NotifyDecision() {}

    /**
     * Avisa quando: já está disponível ({@code now >= availableSince}); ainda não foi avisado nesta
     * janela ({@code notifiedTs < availableSince}); a ferramenta/arma exigida está equipada
     * ({@code gateEquipped}); e ou não está preso, ou a ação é isenta de cadeia.
     */
    public static boolean shouldNotify(long now, long availableSince, long notifiedTs,
                                       boolean gateEquipped, boolean preso, boolean allowedWhileJailed) {
        if (now < availableSince) {
            return false;
        }
        if (notifiedTs >= availableSince) {
            return false;
        }
        if (!gateEquipped) {
            return false;
        }
        return !preso || allowedWhileJailed;
    }
}
