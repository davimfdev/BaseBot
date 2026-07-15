package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotifyDecisionTest {

    private static final long T = 1_000_000L;

    @Test
    void notifiesWhenAvailableEquippedNotJailedAndNotYetNotified() {
        assertTrue(NotifyDecision.shouldNotify(T, T - 1, 0, true, false, false));
    }

    @Test
    void doesNotNotifyBeforeAvailable() {
        assertFalse(NotifyDecision.shouldNotify(T, T + 1, 0, true, false, false));
    }

    @Test
    void doesNotNotifyTwiceInSameWindow() {
        long availableSince = T - 100;
        assertFalse(NotifyDecision.shouldNotify(T, availableSince, availableSince, true, false, false));
        assertFalse(NotifyDecision.shouldNotify(T, availableSince, availableSince + 1, true, false, false));
    }

    @Test
    void notifiesAgainInNewWindow() {
        // já avisou numa janela antiga; nova disponibilidade (availableSince mais recente que o watermark)
        assertTrue(NotifyDecision.shouldNotify(T, T - 10, T - 500, true, false, false));
    }

    @Test
    void gateBlocksWhenToolNotEquipped() {
        assertFalse(NotifyDecision.shouldNotify(T, T - 1, 0, false, false, false));
    }

    @Test
    void jailBlocksUnlessExempt() {
        assertFalse(NotifyDecision.shouldNotify(T, T - 1, 0, true, true, false)); // preso, não isento
        assertTrue(NotifyDecision.shouldNotify(T, T - 1, 0, true, true, true));   // preso, mas daily é isento
    }
}
