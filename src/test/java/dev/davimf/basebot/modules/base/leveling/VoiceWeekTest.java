package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoiceWeekTest {

    private static long at(int year, int month, int day, int hour, int min, int sec) {
        return ZonedDateTime.of(year, month, day, hour, min, sec, 0, VoiceWeek.ZONE)
                .toInstant().toEpochMilli();
    }

    @Test
    void mondayMidnightBelongsToItsOwnWeek() {
        // 2026-07-06 é uma segunda-feira.
        long monday = at(2026, 7, 6, 0, 0, 0);
        assertEquals(monday, VoiceWeek.weekStart(monday));
    }

    @Test
    void sundayNightBelongsToThePreviousWeek() {
        long sunday = at(2026, 7, 5, 23, 59, 59);
        long previousMonday = at(2026, 6, 29, 0, 0, 0);
        assertEquals(previousMonday, VoiceWeek.weekStart(sunday));
    }

    @Test
    void midWeekResolvesToTheMondayBefore() {
        long thursday = at(2026, 7, 9, 15, 30, 0);
        assertEquals(at(2026, 7, 6, 0, 0, 0), VoiceWeek.weekStart(thursday));
    }

    @Test
    void windowInsideOneWeekYieldsOneSlice() {
        long from = at(2026, 7, 8, 10, 0, 0);
        long to = at(2026, 7, 8, 10, 1, 0);
        List<VoiceWeek.Slice> slices = VoiceWeek.splitByWeek(from, to);
        assertEquals(1, slices.size());
        assertEquals(at(2026, 7, 6, 0, 0, 0), slices.get(0).weekStart());
        assertEquals(60_000L, slices.get(0).durationMs());
    }

    @Test
    void windowCrossingMondayIsSplitAndPreservesTotalDuration() {
        long from = at(2026, 7, 5, 23, 59, 30);   // domingo
        long to = at(2026, 7, 6, 0, 0, 30);       // segunda
        List<VoiceWeek.Slice> slices = VoiceWeek.splitByWeek(from, to);

        assertEquals(2, slices.size());
        assertEquals(at(2026, 6, 29, 0, 0, 0), slices.get(0).weekStart());
        assertEquals(at(2026, 7, 6, 0, 0, 0), slices.get(1).weekStart());
        assertEquals(30_000L, slices.get(0).durationMs());
        assertEquals(30_000L, slices.get(1).durationMs());
        assertEquals(to - from, slices.stream().mapToLong(VoiceWeek.Slice::durationMs).sum());
    }

    @Test
    void emptyOrInvertedWindowYieldsNoSlices() {
        long t = at(2026, 7, 8, 10, 0, 0);
        assertTrue(VoiceWeek.splitByWeek(t, t).isEmpty());
        assertTrue(VoiceWeek.splitByWeek(t, t - 1000).isEmpty());
    }
}
