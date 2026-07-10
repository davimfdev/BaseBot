package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoicePendingTest {

    private static final long WEEK = VoiceWeek.weekStart(1_700_000_000_000L);

    @Test
    void ineligibleMemberHasNoPendingTime() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 1_000, WEEK + 61_000, WEEK, false));
    }

    @Test
    void pendingIsTheGapSinceTheWatermark() {
        assertEquals(30_000, VoicePending.pendingMs(WEEK + 1_000, WEEK + 31_000, WEEK, true));
    }

    @Test
    void watermarkBeforeTheWeekStartIsClippedToTheWeekStart() {
        // Sessão aberta no domingo: só o tempo DESTA semana conta para esta semana.
        long lastWeek = WEEK - 600_000;
        assertEquals(45_000, VoicePending.pendingMs(lastWeek, WEEK + 45_000, WEEK, true));
    }

    @Test
    void watermarkAheadOfNowYieldsZeroNotNegative() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 90_000, WEEK + 30_000, WEEK, true));
    }

    @Test
    void watermarkExactlyAtNowYieldsZero() {
        assertEquals(0, VoicePending.pendingMs(WEEK + 5_000, WEEK + 5_000, WEEK, true));
    }
}
