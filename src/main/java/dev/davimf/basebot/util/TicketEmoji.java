package dev.davimf.basebot.util;

/**
 * Validates ticket-category emojis. Only unicode emojis work in Discord channel names —
 * custom emojis ({@code <:name:id>}), {@code :shortcodes:} and plain text do not — so the
 * emoji suffix must be a bare unicode emoji (BOTSPECS Module 2).
 */
public final class TicketEmoji {

    private TicketEmoji() {}

    /** Returns the trimmed emoji if it is channel-name-safe (unicode), otherwise "". */
    public static String channelSafe(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        // Custom emoji / shortcode markers are never valid in channel names.
        if (s.indexOf('<') >= 0 || s.indexOf('>') >= 0 || s.indexOf(':') >= 0) {
            return "";
        }
        // ASCII letters/digits mean it's text, not an emoji.
        boolean hasAscii = s.chars().anyMatch(c ->
                (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
        return hasAscii ? "" : s;
    }
}
