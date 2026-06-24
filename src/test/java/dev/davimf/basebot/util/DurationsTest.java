package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(30_000L, Durations.parse("30s").orElseThrow());
        assertEquals(600_000L, Durations.parse("10m").orElseThrow());
        assertEquals(3_600_000L, Durations.parse("1h").orElseThrow());
        assertEquals(86_400_000L, Durations.parse("1d").orElseThrow());
    }

    @Test
    void parsesCombinedAndSpaced() {
        assertEquals(5_400_000L, Durations.parse("1h30m").orElseThrow());
        assertEquals(90_000L, Durations.parse("1m 30s").orElseThrow());
    }

    @Test
    void clampsToMax() {
        assertEquals(Durations.MAX_MILLIS, Durations.parse("999d").orElseThrow());
    }

    @Test
    void rejectsInvalid() {
        assertEquals(OptionalLong.empty(), Durations.parse("abc"));
        assertEquals(OptionalLong.empty(), Durations.parse("10"));
        assertEquals(OptionalLong.empty(), Durations.parse("10x"));
        assertEquals(OptionalLong.empty(), Durations.parse(""));
        assertEquals(OptionalLong.empty(), Durations.parse(null));
    }

    @Test
    void formats() {
        assertEquals("10m", Durations.format(600_000L));
        assertEquals("1h 30m", Durations.format(5_400_000L));
        assertEquals("0s", Durations.format(0L));
    }
}
