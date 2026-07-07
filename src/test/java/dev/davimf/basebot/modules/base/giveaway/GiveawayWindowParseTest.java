package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayWindowParseTest {
    @Test
    void parsesValid() {
        assertArrayEquals(new int[]{20, 23}, GiveawayWindow.parse("20-23"));
        assertArrayEquals(new int[]{0, 24}, GiveawayWindow.parse(" 0 - 24 "));
    }

    @Test
    void rejectsInvalid() {
        assertNull(GiveawayWindow.parse("abc"));
        assertNull(GiveawayWindow.parse("23-20"));
        assertNull(GiveawayWindow.parse("25-30"));
        assertNull(GiveawayWindow.parse(null));
    }
}
