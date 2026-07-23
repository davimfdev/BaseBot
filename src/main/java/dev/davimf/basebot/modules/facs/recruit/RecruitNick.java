package dev.davimf.basebot.modules.facs.recruit;

/** Monta o apelido pós-aceite {@code "{Nome} | {ID}"} limitado a 32 chars (limite do Discord). */
public final class RecruitNick {

    private static final int MAX = 32;
    private static final String SEP = " | ";

    private RecruitNick() {}

    public static String format(String nome, String idJogo) {
        String n = sanitize(nome);
        String id = sanitize(idJogo);
        if (n.isEmpty()) {
            n = "Membro";
        }
        String full = n + SEP + id;
        if (full.length() <= MAX) {
            return full;
        }
        int available = MAX - (SEP.length() + id.length());
        if (available >= 1) {
            return n.substring(0, Math.min(n.length(), available)).strip() + SEP + id;
        }
        return full.substring(0, MAX);
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\n\\r\\t]", " ").replaceAll("\\s+", " ").strip();
    }
}
