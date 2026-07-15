package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DailyResetTest {

    private static long millisAt(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), DailyReset.ZONE).toInstant().toEpochMilli();
    }

    @Test
    void todayMidnightIsStartOfLocalDay() {
        long now = millisAt(2026, 7, 15, 14, 30);
        assertEquals(millisAt(2026, 7, 15, 0, 0), DailyReset.todayMidnightMillis(now));
    }

    @Test
    void nextMidnightIsStartOfNextLocalDay() {
        long now = millisAt(2026, 7, 15, 14, 30);
        assertEquals(millisAt(2026, 7, 16, 0, 0), DailyReset.nextMidnightMillis(now));
    }

    @Test
    void availableWhenLastClaimWasYesterday() {
        long now = millisAt(2026, 7, 15, 0, 1);
        long yesterday = millisAt(2026, 7, 14, 23, 59);
        assertTrue(DailyReset.available(yesterday, now));
    }

    @Test
    void notAvailableWhenAlreadyClaimedToday() {
        long now = millisAt(2026, 7, 15, 23, 0);
        long earlierToday = millisAt(2026, 7, 15, 8, 0);
        assertFalse(DailyReset.available(earlierToday, now));
    }

    @Test
    void availableWhenNeverClaimed() {
        assertTrue(DailyReset.available(0, millisAt(2026, 7, 15, 12, 0)));
    }
}
