package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModReasonTest {

    @Test
    void joinsModeratorAndReason() {
        assertEquals("davi#0 • spam", ModReason.of("davi#0", "spam"));
    }

    @Test
    void blankReasonGivesModeratorOnly() {
        assertEquals("davi#0", ModReason.of("davi#0", "  "));
        assertEquals("davi#0", ModReason.of("davi#0", null));
    }

    @Test
    void truncatesToAuditLimit() {
        String longReason = "x".repeat(1000);
        assertEquals(480, ModReason.of("m", longReason).length());
    }
}
