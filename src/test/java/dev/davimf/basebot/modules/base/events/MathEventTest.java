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
    void generatedProblemsAreAlwaysValidNonNegativeIntegers() {
        java.util.Random r = new java.util.Random(42);
        for (int i = 0; i < 2000; i++) {
            MathEvent.Problem p = MathEvent.generate(r);
            assertFalse(p.prompt().isBlank(), "enunciado não vazio");
            assertFalse(p.answer().isBlank(), "resposta não vazia");
            long ans = Long.parseLong(p.answer());   // deve ser um inteiro puro
            assertTrue(ans >= 0, "resposta não-negativa, veio " + ans + " para " + p.prompt());
            assertFalse(p.prompt().contains("/"), "sem divisão (respostas inteiras): " + p.prompt());
        }
    }

    @Test
    void coversMultipleTemplates() {
        java.util.Random r = new java.util.Random(1);
        java.util.Set<String> shapes = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            String prompt = MathEvent.generate(r).prompt();
            shapes.add(prompt.replaceAll("\\d+", "n"));  // normaliza números
        }
        assertTrue(shapes.size() >= 4, "vários formatos de conta, veio " + shapes);
    }
}
