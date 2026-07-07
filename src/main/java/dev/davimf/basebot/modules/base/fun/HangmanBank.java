package dev.davimf.basebot.modules.base.fun;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Banco embutido de palavras da forca (PT), cada uma com um tema/dica. */
public final class HangmanBank {

    /** Uma palavra e o tema exibido como dica. */
    public record Entry(String word, String theme) {}

    private static Entry e(String word, String theme) { return new Entry(word, theme); }

    private static final List<Entry> ENTRIES = List.of(
            // Animais
            e("cachorro", "Animais"), e("elefante", "Animais"), e("borboleta", "Animais"),
            e("cavalo", "Animais"), e("tartaruga", "Animais"), e("papagaio", "Animais"),
            e("girafa", "Animais"),
            // Natureza
            e("girassol", "Natureza"), e("montanha", "Natureza"), e("relâmpago", "Natureza"),
            e("tempestade", "Natureza"), e("cachoeira", "Natureza"), e("floresta", "Natureza"),
            // Comida
            e("abóbora", "Comida"), e("chocolate", "Comida"), e("melancia", "Comida"),
            e("lasanha", "Comida"), e("brigadeiro", "Comida"), e("morango", "Comida"),
            // Objetos
            e("computador", "Objetos"), e("guitarra", "Objetos"), e("telescópio", "Objetos"),
            e("bicicleta", "Objetos"), e("guardachuva", "Objetos"),
            // Lugares
            e("biblioteca", "Lugares"), e("aeroporto", "Lugares"), e("castelo", "Lugares"),
            e("mercado", "Lugares"));

    private HangmanBank() {}

    public static Entry random() {
        return ENTRIES.get(ThreadLocalRandom.current().nextInt(ENTRIES.size()));
    }
}
