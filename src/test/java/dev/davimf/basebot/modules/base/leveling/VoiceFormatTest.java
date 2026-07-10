package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceFormatTest {

    @Test
    void zeroAndNegativeReadAsZeroSeconds() {
        assertEquals("0s", VoiceFormat.precise(0));
        assertEquals("0s", VoiceFormat.precise(-5));
    }

    @Test
    void subSecondTruncatesToZero() {
        assertEquals("0s", VoiceFormat.precise(999));
    }

    @Test
    void underOneMinuteShowsOnlySeconds() {
        assertEquals("1s", VoiceFormat.precise(1_000));
        assertEquals("59s", VoiceFormat.precise(59_000));
    }

    @Test
    void underOneHourShowsMinutesAndZeroPaddedSeconds() {
        assertEquals("1m 00s", VoiceFormat.precise(60_000));
        assertEquals("47m 03s", VoiceFormat.precise((47 * 60 + 3) * 1_000L));
        assertEquals("59m 59s", VoiceFormat.precise(3_599_000));
    }

    @Test
    void oneHourOrMoreShowsAllThreeUnits() {
        assertEquals("1h 00m 00s", VoiceFormat.precise(3_600_000));
        assertEquals("12h 34m 07s", VoiceFormat.precise((12 * 3600 + 34 * 60 + 7) * 1_000L));
    }

    @Test
    void hoursDoNotWrapAtTwentyFour() {
        assertEquals("100h 00m 00s", VoiceFormat.precise(100 * 3_600_000L));
    }
}
