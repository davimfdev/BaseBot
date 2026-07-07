package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VoiceWindowTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, SP).toInstant().toEpochMilli();
    }

    @Test
    void sessionInsideWindowOverlaps() {
        assertTrue(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 20, 30), at(2026, 6, 1, 21, 0), 20, 23, SP));
    }

    @Test
    void sessionBeforeWindowDoesNotOverlap() {
        assertFalse(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 18, 0), at(2026, 6, 1, 19, 0), 20, 23, SP));
    }

    @Test
    void sessionCrossingIntoWindowOverlaps() {
        assertTrue(VoiceWindow.overlapsDailyWindow(at(2026, 6, 1, 19, 0), at(2026, 6, 1, 20, 30), 20, 23, SP));
    }
}
