package dev.davimf.basebot.config;

/**
 * Immutable, fully-resolved bot configuration. Built by {@link ConfigLoader} from
 * {@code config.yml} with environment-variable overrides applied.
 *
 * <p>Records keep this a plain data carrier; there is no logic here on purpose.
 */
public record BotConfig(
        Discord discord,
        Postgres postgres,
        Sqlite sqlite,
        Tickets tickets,
        RateLimit rateLimit
) {

    public record Discord(String token, String devGuildId) {
        public boolean hasDevGuild() {
            return devGuildId != null && !devGuildId.isBlank();
        }
    }

    /**
     * Plain JDBC Postgres settings. Intentionally driver-agnostic so the guild-config
     * store can move from Supabase to a dedicated Neon schema without code changes —
     * only {@link #url}/{@link #schema} change.
     */
    public record Postgres(
            String url,
            String username,
            String password,
            int maxPoolSize,
            String schema
    ) {}

    public record Sqlite(String path) {}

    /** Ticket transcript pipeline -> davimf.dev. Mirrors netlify ticket-store contract. */
    public record Tickets(
            String ingestUrl,
            String ingestSecret,
            String viewBaseUrl,
            int pbkdf2Iterations,
            String source
    ) {
        /** Public URL a user clicks to view/decrypt a transcript. */
        public String viewUrl(String ticketId) {
            String base = viewBaseUrl.endsWith("/")
                    ? viewBaseUrl.substring(0, viewBaseUrl.length() - 1)
                    : viewBaseUrl;
            return base + "/" + ticketId;
        }
    }

    public record RateLimit(
            long embedDebounceMillis,
            int ghostPingBatchSize,
            int ghostPingBatchIntervalSeconds
    ) {}
}
