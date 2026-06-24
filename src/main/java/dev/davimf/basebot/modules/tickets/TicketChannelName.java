package dev.davimf.basebot.modules.tickets;

import java.util.Locale;

/** Builds a valid Discord channel name for a ticket: {@code <emoji>category-user}. */
public final class TicketChannelName {

    private static final int MAX = 90;

    private TicketChannelName() {}

    public static String of(String emoji, String categoryName, String username) {
        String prefix = (emoji != null && !emoji.isBlank()) ? emoji : "";
        String name = prefix + slug(categoryName) + "-" + slug(username);
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
