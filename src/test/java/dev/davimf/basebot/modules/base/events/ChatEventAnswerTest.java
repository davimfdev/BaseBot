package dev.davimf.basebot.modules.base.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatEventAnswerTest {
    @Test
    void matchesTrimAndCaseInsensitive() {
        assertTrue(ChatEventAnswer.matches("  Banana ", "banana"));
        assertTrue(ChatEventAnswer.matches("84", "84"));
    }

    @Test
    void doesNotMatchDifferent() {
        assertFalse(ChatEventAnswer.matches("maca", "banana"));
        assertFalse(ChatEventAnswer.matches("", "banana"));
    }
}
