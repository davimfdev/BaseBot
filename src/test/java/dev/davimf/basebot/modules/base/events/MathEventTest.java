package dev.davimf.basebot.modules.base.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathEventTest {
    @Test
    void computesOperations() {
        assertEquals(84, MathEvent.compute(12, '×', 7));
        assertEquals(19, MathEvent.compute(12, '+', 7));
        assertEquals(5, MathEvent.compute(12, '-', 7));
    }

    @Test
    void generatedProblemAnswerMatchesPrompt() {
        MathEvent.Problem p = MathEvent.generate(new java.util.Random(42));
        assertNotNull(p.prompt());
        assertFalse(p.answer().isBlank());
        assertTrue(Long.parseLong(p.answer()) >= 0);
    }
}
