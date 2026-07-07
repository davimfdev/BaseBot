package dev.davimf.basebot.modules.base.fun;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

/** Estado puro da forca. Comparação normaliza acentos e caixa; exibe a palavra original. */
public record HangmanState(String word, Set<Character> tried, int wrongCount) {

    public static final int MAX_LIVES = 6;

    public static HangmanState start(String word) {
        return new HangmanState(word, Set.of(), 0);
    }

    public int lives() {
        return MAX_LIVES - wrongCount;
    }

    public HangmanState guessLetter(char c) {
        char nc = normChar(c);
        if (nc == 0 || tried.contains(nc)) {
            return this;
        }
        Set<Character> next = new HashSet<>(tried);
        next.add(nc);
        boolean hit = norm(word).indexOf(nc) >= 0;
        return new HangmanState(word, next, hit ? wrongCount : wrongCount + 1);
    }

    public HangmanState guessWord(String guess) {
        if (norm(guess).equals(norm(word))) {
            Set<Character> next = new HashSet<>(tried);
            for (char ch : norm(word).toCharArray()) {
                if (Character.isLetter(ch)) {
                    next.add(ch);
                }
            }
            return new HangmanState(word, next, wrongCount);
        }
        return new HangmanState(word, tried, wrongCount + 1);
    }

    public boolean won() {
        for (char ch : norm(word).toCharArray()) {
            if (Character.isLetter(ch) && !tried.contains(ch)) {
                return false;
            }
        }
        return true;
    }

    public boolean lost() {
        return lives() <= 0;
    }

    /** Palavra mascarada: letras adivinhadas mostram o char original; o resto vira {@code _}. */
    public String masked() {
        StringBuilder sb = new StringBuilder();
        String nw = norm(word);
        for (int i = 0; i < word.length(); i++) {
            char original = word.charAt(i);
            char n = i < nw.length() ? nw.charAt(i) : normChar(original);
            if (!Character.isLetter(n)) {
                sb.append(original);
            } else if (tried.contains(n)) {
                sb.append(original);
            } else {
                sb.append('_');
            }
            sb.append(' ');
        }
        return sb.toString().strip();
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private static char normChar(char c) {
        String n = norm(String.valueOf(c));
        return n.isEmpty() ? 0 : n.charAt(0);
    }
}
