package dev.davimf.basebot.modules.base.events;

/** Checagem pura de resposta de evento (trim + case-insensitive). */
public final class ChatEventAnswer {
    private ChatEventAnswer() {}

    public static boolean matches(String input, String answer) {
        return input != null && answer != null && input.trim().equalsIgnoreCase(answer.trim());
    }
}
