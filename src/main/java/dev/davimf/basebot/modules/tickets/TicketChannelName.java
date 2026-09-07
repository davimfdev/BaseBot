package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.util.Emojis;

import java.util.Locale;

/**
 * Builds ticket channel names (BOTSPECS Module 2). The scheme encodes the ticket's state
 * in the prefix emoji + a katakana middle dot separator:
 * <ul>
 *   <li>open: {@code " + Emojis.of(Emojis.UNLOCK, "🔓") + "・<creator>}</li>
 *   <li>assumed: {@code <category emoji or " + Emojis.of(Emojis.LOCK, "🔒") + ">・<staff>}</li>
 *   <li>renamed: {@code <category emoji or " + Emojis.of(Emojis.LOCK, "🔒") + ">・<new name>}</li>
 * </ul>
 */
public final class TicketChannelName {

    /** Open padlock used while the ticket is unassigned. */
    public static final String OPEN_LOCK = "" + Emojis.of(Emojis.UNLOCK, "🔓") + "";
    /** Closed padlock used once assumed/renamed when the category has no emoji. */
    public static final String CLOSED_LOCK = "" + Emojis.of(Emojis.LOCK, "🔒") + "";
    /** Katakana middle dot separator between the prefix and the name. */
    public static final String SEP = "・";

    private static final int MAX = 90;

    private TicketChannelName() {}

    /** {@code " + Emojis.of(Emojis.UNLOCK, "🔓") + "・<creator>} — the freshly opened, unassigned ticket. */
    public static String opened(String creatorName) {
        return clamp(OPEN_LOCK + SEP + slug(creatorName));
    }

    /** {@code <category emoji or " + Emojis.of(Emojis.LOCK, "🔒") + ">・<staff>} — once a staff member assumes it. */
    public static String assumed(String categoryEmoji, String staffName) {
        return clamp(prefix(categoryEmoji) + SEP + slug(staffName));
    }

    /** {@code <category emoji or " + Emojis.of(Emojis.LOCK, "🔒") + ">・<name>} — keeps the category emoji on rename. */
    public static String renamed(String categoryEmoji, String newName) {
        return clamp(prefix(categoryEmoji) + SEP + slug(newName));
    }

    private static String prefix(String emoji) {
        return (emoji != null && !emoji.isBlank()) ? emoji : CLOSED_LOCK;
    }

    private static String clamp(String name) {
        return name.length() > MAX ? name.substring(0, MAX) : name;
    }

    /** Lowercase, non-alphanumerics → single hyphens, trimmed; falls back to "ticket". */
    static String slug(String s) {
        if (s == null) {
            return "ticket";
        }
        String x = s.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return x.isEmpty() ? "ticket" : x;
    }
}
