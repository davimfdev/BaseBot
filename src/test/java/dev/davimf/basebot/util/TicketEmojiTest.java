// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: TicketEmojiTest
// [OUTLINE END]



package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketEmojiTest {

    @Test
    void keepsUnicodeEmoji() {
        assertEquals("🛠️", TicketEmoji.channelSafe("🛠️"));
        assertEquals("🎫", TicketEmoji.channelSafe("  🎫 "));
    }

    @Test
    void rejectsCustomEmojiAndShortcodes() {
        assertEquals("", TicketEmoji.channelSafe("<:wrench:123456789>"));
        assertEquals("", TicketEmoji.channelSafe(":wrench:"));
    }

    @Test
    void rejectsPlainTextAndDigits() {
        assertEquals("", TicketEmoji.channelSafe("abc"));
        assertEquals("", TicketEmoji.channelSafe("v2"));
        assertEquals("", TicketEmoji.channelSafe(""));
        assertEquals("", TicketEmoji.channelSafe(null));
    }
}
