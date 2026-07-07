package dev.davimf.basebot.modules.base.events;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Banco estático de perguntas de quiz. */
public final class QuizBank {
    public record Question(String text, List<String> options, int correct) {}

    private static final List<Question> QUESTIONS = List.of(
            new Question("Qual a capital do Brasil?", List.of("Rio de Janeiro", "Brasília", "São Paulo", "Salvador"), 1),
            new Question("Quanto é 6 × 7?", List.of("42", "36", "48", "13"), 0),
            new Question("Qual planeta é o 'Planeta Vermelho'?", List.of("Vênus", "Júpiter", "Marte", "Saturno"), 2),
            new Question("Quantos lados tem um hexágono?", List.of("5", "6", "7", "8"), 1),
            new Question("Qual é o maior oceano?", List.of("Atlântico", "Índico", "Ártico", "Pacífico"), 3));

    private QuizBank() {}

    public static Question random() {
        return QUESTIONS.get(ThreadLocalRandom.current().nextInt(QUESTIONS.size()));
    }
}
