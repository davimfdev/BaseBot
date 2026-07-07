package dev.davimf.basebot.modules.base.events;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Banco estático de palavras para a corrida de digitação. */
public final class WordBank {
    private static final List<String> WORDS = List.of(
            "banana", "guitarra", "montanha", "relogio", "cachorro", "janela",
            "foguete", "abacaxi", "tijolo", "girassol", "caverna", "bicicleta");

    private WordBank() {}

    public static String random() {
        return WORDS.get(ThreadLocalRandom.current().nextInt(WORDS.size()));
    }
}
