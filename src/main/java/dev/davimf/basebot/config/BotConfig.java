// [OUTLINE START]
// Package: dev.davimf.basebot.config
// 
// Record: BotConfig
// 
// Record Components:
//   - Record Component : public final Discord discord
//   - Record Component : public final Postgres postgres
//   - Record Component : public final Sqlite sqlite
//   - Record Component : public final Tickets tickets
//   - Record Component : public final RateLimit rateLimit
// 
// Record: Discord
// 
// Record Components:
//   - Record Component : public final String token
//   - Record Component : public final String devGuildId
// 
// Methods:
//   - `Method` : `public boolean hasDevGuild()`
//   - `Method` : `public java.util.List<String> devGuildIds()`
// 
// Record: Postgres
// 
// Record Components:
//   - Record Component : public final String url
//   - Record Component : public final String username
//   - Record Component : public final String password
//   - Record Component : public final int maxPoolSize
//   - Record Component : public final String schema
// 
// Record: Sqlite
// 
// Record Components:
//   - Record Component : public final String path
// 
// Record: Tickets
// 
// Record Components:
//   - Record Component : public final String ingestUrl
//   - Record Component : public final String ingestSecret
//   - Record Component : public final String viewBaseUrl
//   - Record Component : public final int pbkdf2Iterations
//   - Record Component : public final String source
// 
// Methods:
//   - `Method` : `public String viewUrl(String ticketId)`
// 
// Record: RateLimit
// 
// Record Components:
//   - Record Component : public final long embedDebounceMillis
//   - Record Component : public final int ghostPingBatchSize
//   - Record Component : public final int ghostPingBatchIntervalSeconds
// [OUTLINE END]



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

    public record Discord(String token, String devGuildId, String vaultGuildId) {
        public boolean hasDevGuild() {
            return devGuildId != null && !devGuildId.isBlank();
        }

        /** True when a central attachment-vault guild is configured. */
        public boolean hasVault() {
            return vaultGuildId != null && !vaultGuildId.isBlank();
        }

        /** Dev guild ids (comma-separated in config) commands are restricted to; empty in prod. */
        public java.util.List<String> devGuildIds() {
            if (!hasDevGuild()) {
                return java.util.List.of();
            }
            return java.util.Arrays.stream(devGuildId.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
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
