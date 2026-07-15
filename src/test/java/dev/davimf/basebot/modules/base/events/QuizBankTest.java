package dev.davimf.basebot.modules.base.events;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class QuizBankTest {

    @Test
    void everyQuestionIsWellFormed() {
        for (QuizBank.Question q : QuizBank.all()) {
            assertEquals(5, q.options().size(), "5 alternativas: " + q.text());
            assertTrue(q.correct() >= 0 && q.correct() < q.options().size(),
                    "índice correto no intervalo: " + q.text());
            assertFalse(q.text().isBlank(), "enunciado não vazio");
            assertEquals(q.options().size(), new HashSet<>(q.options()).size(),
                    "alternativas sem duplicatas: " + q.text());
            for (String opt : q.options()) {
                assertFalse(opt.isBlank(), "alternativa não vazia: " + q.text());
            }
        }
    }

    @Test
    void hasSubstantialBank() {
        assertTrue(QuizBank.all().size() >= 50,
                "banco expandido, veio " + QuizBank.all().size());
    }

    @Test
    void shufflePreservesCorrectAnswer() {
        Random r = new Random(7);
        for (QuizBank.Question q : QuizBank.all()) {
            String expected = q.options().get(q.correct());
            for (int i = 0; i < 20; i++) {
                QuizBank.Question s = q.shuffled(r);
                assertEquals(expected, s.options().get(s.correct()),
                        "resposta certa preservada após embaralhar: " + q.text());
                assertEquals(new HashSet<>(q.options()), new HashSet<>(s.options()),
                        "mesmo conjunto de alternativas");
            }
        }
    }

    @Test
    void shuffleActuallyMovesTheAnswerAround() {
        // Com 5 posições, sortear 40 vezes deve colocar a resposta em mais de uma posição.
        QuizBank.Question q = QuizBank.all().get(0);
        Random r = new Random(123);
        HashSet<Integer> positions = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            positions.add(q.shuffled(r).correct());
        }
        assertTrue(positions.size() > 1, "posição da resposta varia entre sorteios");
    }

    @Test
    void randomReturnsValidQuestion() {
        for (int i = 0; i < 50; i++) {
            QuizBank.Question q = QuizBank.random();
            assertEquals(5, q.options().size());
            assertTrue(q.correct() >= 0 && q.correct() < 5);
        }
    }
}
