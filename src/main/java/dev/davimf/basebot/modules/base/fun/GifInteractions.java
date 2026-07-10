package dev.davimf.basebot.modules.base.fun;

import java.util.List;

/**
 * Catálogo das ações de interação com GIF (nekos.best): abraçar, beijar, socar, etc. Cada
 * {@link Spec} vira um subcomando de {@code /interagir}.
 *
 * <p>Puro, sem JDA — a frase da ação é testável sem mocks. O comando em si (que resolve o alvo,
 * bloqueia usar em si mesmo e busca o GIF) fica em {@code commands/InteragirCommand}.
 */
public final class GifInteractions {

    /**
     * @param name        nome do slash command (ASCII, minúsculo)
     * @param description descrição exibida no Discord
     * @param category    categoria do nekos.best (ex.: {@code kiss}, {@code punch}, {@code shoot})
     * @param verb        verbo da ação, ex.: {@code "deu um beijo em"}
     * @param emoji       emoji ao fim da frase
     */
    public record Spec(String name, String description, String category, String verb, String emoji) {
        /** Frase da ação: {@code "{actor} {verb} {target} {emoji}"}. */
        public String message(String actorMention, String targetMention) {
            return actorMention + " " + verb + " " + targetMention + " " + emoji;
        }
    }

    /** Ordem = ordem de registro. Os dois primeiros preservam os comandos que já existiam. */
    public static final List<Spec> CATALOG = List.of(
            new Spec("abracar", "Abraça alguém.", "hug", "abraçou", "🤗"),
            new Spec("toca_aqui", "Faz um carinho em alguém.", "pat", "fez um carinho em", "🥰"),
            new Spec("beijo", "Beija alguém.", "kiss", "deu um beijo em", "😚"),
            new Spec("soco", "Dá um soco em alguém.", "punch", "deu um soco em", "👊"),
            new Spec("peteco", "Manda um peteco peteco em alguém.", "shoot", "mandou um peteco peteco em", "🔫"),
            new Spec("tapa", "Dá um tapa em alguém.", "slap", "deu um tapa em", "👋"),
            new Spec("morder", "Morde alguém.", "bite", "mordeu", "😬"),
            new Spec("cutucar", "Cutuca alguém.", "poke", "cutucou", "👉"),
            new Spec("cocegas", "Faz cócegas em alguém.", "tickle", "fez cócegas em", "🤭"),
            new Spec("paulada", "Dá uma paulada em alguém.", "bonk", "deu uma paulada em", "🔨"),
            new Spec("selinho", "Dá um selinho em alguém.", "peck", "deu um selinho em", "😚"),
            new Spec("chute", "Dá um chute em alguém.", "kick", "deu um chute em", "🦵"));

    /** A spec cujo {@link Spec#name()} casa com {@code name}, ou {@code null}. */
    public static Spec byName(String name) {
        for (Spec s : CATALOG) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    private GifInteractions() {}
}
