package dev.davimf.basebot.util;

import net.dv8tion.jda.api.entities.User;

/** Constrói o texto de motivo do audit log do Discord embutindo o moderador humano,
 *  já que o bot é o ator registrado. Formato: "{moderador} • {motivo}". */
public final class ModReason {

    private static final int MAX = 480;

    private ModReason() {}

    public static String of(User moderator, String motivo) {
        return of(moderator.getAsTag(), motivo);
    }

    public static String of(String moderatorTag, String motivo) {
        String s = (motivo == null || motivo.isBlank())
                ? moderatorTag
                : moderatorTag + " • " + motivo;
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }
}
