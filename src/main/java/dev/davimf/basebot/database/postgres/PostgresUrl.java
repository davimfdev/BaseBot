package dev.davimf.basebot.database.postgres;

import java.net.URI;

/**
 * Normalizes a PostgreSQL connection string into the pieces HikariCP needs.
 *
 * <p>Neon (and libpq generally) hands out a single connection string with credentials
 * embedded, e.g. {@code postgresql://user:pass@host/db?sslmode=require}. HikariCP wants
 * a {@code jdbc:postgresql://…} URL plus a separate username/password. This converts the
 * URI form to a JDBC URL and extracts the embedded credentials, while leaving an already
 * {@code jdbc:}-prefixed URL untouched. Explicit overrides (e.g. {@code POSTGRES_USER})
 * win over anything embedded in the string.
 */
public final class PostgresUrl {

    private PostgresUrl() {}

    /** The JDBC URL plus resolved credentials (either may be null when unknown). */
    public record Parsed(String jdbcUrl, String user, String password) {}

    public static Parsed normalize(String rawUrl, String userOverride, String passwordOverride) {
        String raw = rawUrl == null ? "" : rawUrl.trim();

        if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
            URI uri = URI.create(raw);
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
            if (uri.getPort() > 0) {
                jdbc.append(':').append(uri.getPort());
            }
            if (uri.getRawPath() != null) {
                jdbc.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                jdbc.append('?').append(uri.getRawQuery());
            }

            String embeddedUser = null;
            String embeddedPassword = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    embeddedUser = userInfo.substring(0, colon);
                    embeddedPassword = userInfo.substring(colon + 1);
                } else {
                    embeddedUser = userInfo;
                }
            }
            return new Parsed(jdbc.toString(),
                    firstNonBlank(userOverride, embeddedUser),
                    firstNonBlank(passwordOverride, embeddedPassword));
        }

        // Already a jdbc: URL (or an unrecognized form): pass through with explicit creds.
        return new Parsed(raw, blankToNull(userOverride), blankToNull(passwordOverride));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return blankToNull(b);
    }
}
