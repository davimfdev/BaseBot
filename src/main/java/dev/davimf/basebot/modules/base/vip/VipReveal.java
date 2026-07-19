package dev.davimf.basebot.modules.base.vip;

/** Decisão pura de revelar/ocultar a call VIP conforme ocupação. */
public final class VipReveal {
    private VipReveal() {}
    public enum RevealAction { REVEAL, HIDE, NONE }

    public static RevealAction decide(boolean revealEnabled, int allowedHumansInCall, boolean currentlyRevealed) {
        if (!revealEnabled) return currentlyRevealed ? RevealAction.HIDE : RevealAction.NONE;
        if (allowedHumansInCall >= 1 && !currentlyRevealed) return RevealAction.REVEAL;
        if (allowedHumansInCall == 0 && currentlyRevealed) return RevealAction.HIDE;
        return RevealAction.NONE;
    }
}
