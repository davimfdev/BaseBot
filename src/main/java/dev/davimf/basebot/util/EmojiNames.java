package dev.davimf.basebot.util;

/** Discord custom-emoji name rules: 2-32 chars of letters, digits and underscores. */
public final class EmojiNames {

    private EmojiNames() {}

    public static boolean isValid(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{2,32}");
    }

    /** Coerces arbitrary input into a valid emoji name (invalid chars -> '_', clamped 2-32). */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "emoji";
        }
        String s = raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        while (s.length() < 2) {
            s = s + "_";
        }
        return s;
    }
}
