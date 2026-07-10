package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceFormatTest {

    @Test
    void zeroReadsAsZeroMinutes() {
        assertEquals("0m", VoiceFormat.duration(0));
    }

    @Test
    void underOneMinuteIsCalledOut() {
        assertEquals("menos de 1m", VoiceFormat.duration(59_000));
    }

    @Test
    void underOneHourShowsOnlyMinutes() {
        assertEquals("34m", VoiceFormat.duration(34 * 60_000L));
    }

    @Test
    void oneHourOrMoreShowsHoursAndMinutes() {
        assertEquals("1h 00m", VoiceFormat.duration(60 * 60_000L));
        assertEquals("12h 34m", VoiceFormat.duration((12 * 60 + 34) * 60_000L));
    }

    @Test
    void hoursDoNotWrapAtTwentyFour() {
        assertEquals("100h 00m", VoiceFormat.duration(100 * 60 * 60_000L));
    }
}
