package dev.davimf.basebot.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmojiNamesTest {

    @Test
    void validNamesAreAlnumUnderscore2to32() {
        assertTrue(EmojiNames.isValid("cool_emoji"));
        assertTrue(EmojiNames.isValid("ab"));
        assertFalse(EmojiNames.isValid("a"));            // too short
        assertFalse(EmojiNames.isValid("has space"));    // space
        assertFalse(EmojiNames.isValid("emoji!"));       // punctuation
        assertFalse(EmojiNames.isValid(null));
    }

    @Test
    void sanitizeReplacesInvalidCharsAndClampsLength() {
        assertEquals("my_emoji_", EmojiNames.sanitize("my emoji!"));
        assertEquals("a_", EmojiNames.sanitize("a"));    // padded to min length 2
        assertEquals(32, EmojiNames.sanitize("x".repeat(40)).length());
    }
}
