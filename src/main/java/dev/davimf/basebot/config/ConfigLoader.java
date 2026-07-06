// [OUTLINE START]
// Package: dev.davimf.basebot.config
// 
// Class: ConfigLoader
// 
// Constructors:
//   - `Constructor` : `private ConfigLoader()`
// 
// Methods:
//   - `Method` : `public static BotConfig load()`
//   - `Method` : `public static BotConfig load(Path file)`
//   - `Method` : `private static JsonNode readYamlOrEmpty(Path file)`
//   - `Method` : `private static String str(JsonNode node, String field, String def)`
//   - `Method` : `private static int intVal(JsonNode node, String field, int def)`
//   - `Method` : `private static long longVal(JsonNode node, String field, long def)`
//   - `Method` : `private static String env(DotEnv dotenv, String key, String fallback)`
//   - `Method` : `private static int envInt(DotEnv dotenv, String key, int fallback)`
//   - `Method` : `private static long envLong(DotEnv dotenv, String key, long fallback)`
//   - `Method` : `private static <T> T parseEnv(DotEnv dotenv, String key, T fallback, Function<String, T> parser)`
//   - `Method` : `private static String require(DotEnv dotenv, String envKey, String yamlValue)`
// [OUTLINE END]



package dev.davimf.basebot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Loads {@link BotConfig} from a YAML file, overlaying a {@code .env} file and the
 * process environment.
 *
 * <p>Precedence (highest first): process environment variable -&gt; {@code .env} file
 * value -&gt; YAML value -&gt; built-in default. Secrets can therefore live in a
 * git-ignored {@code .env} locally, or be supplied purely as env vars in production,
 * while {@code config.yml} stays optional. Process-vs-file precedence is handled by
 * {@link DotEnv#get(String)}.
 */
public final class ConfigLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {}

    /** Loads from {@code config.yml} in the working directory if present. */
    public static BotConfig load() {
        return load(Path.of("config.yml"));
    }

    public static BotConfig load(Path file) {
        DotEnv dotenv = DotEnv.load(Path.of(".env"));
        JsonNode root = readYamlOrEmpty(file);

        JsonNode discord = root.path("discord");
        JsonNode postgres = root.path("postgres");
        JsonNode sqlite = root.path("sqlite");
        JsonNode tickets = root.path("tickets");
        JsonNode rl = root.path("ratelimit");

        return new BotConfig(
                new BotConfig.Discord(
                        require(dotenv, "BOT_TOKEN", str(discord, "token", null)),
                        env(dotenv, "DEV_GUILD_ID", str(discord, "devGuildId", null)),
                        env(dotenv, "VAULT_GUILD_ID", str(discord, "vaultGuildId", null))
                ),
                new BotConfig.Postgres(
                        env(dotenv, "POSTGRES_URL", str(postgres, "url", "jdbc:postgresql://localhost:5432/basebot")),
                        str(postgres, "username", null),
                        str(postgres, "password", null),
                        envInt(dotenv, "POSTGRES_MAX_POOL", intVal(postgres, "maxPoolSize", 5)),
                        env(dotenv, "POSTGRES_SCHEMA", str(postgres, "schema", "public"))
                ),
                new BotConfig.Sqlite(
                        env(dotenv, "SQLITE_PATH", str(sqlite, "path", "data/basebot.db"))
                ),
                new BotConfig.Tickets(
                        env(dotenv, "TICKET_INGEST_URL", str(tickets, "ingestUrl", "https://davimf.dev/api/ticket-store")),
                        env(dotenv, "TICKET_INGEST_SECRET", str(tickets, "ingestSecret", "")),
                        env(dotenv, "TICKET_VIEW_BASE", str(tickets, "viewBaseUrl", "https://davimf.dev/ticket")),
                        envInt(dotenv, "TICKET_PBKDF2_ITERATIONS", intVal(tickets, "pbkdf2Iterations", 210_000)),
                        env(dotenv, "TICKET_SOURCE", str(tickets, "source", "basebot"))
                ),
                new BotConfig.RateLimit(
                        envLong(dotenv, "EMBED_DEBOUNCE_MS", longVal(rl, "embedDebounceMillis", 5_000L)),
                        envInt(dotenv, "GHOST_PING_BATCH", intVal(rl, "ghostPingBatchSize", 5)),
                        envInt(dotenv, "GHOST_PING_INTERVAL_S", intVal(rl, "ghostPingBatchIntervalSeconds", 30))
                )
        );
    }

    private static JsonNode readYamlOrEmpty(Path file) {
        try {
            if (Files.exists(file)) {
                return YAML.readTree(Files.readAllBytes(file));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse config file: " + file, e);
        }
        // No file: rely entirely on env / .env / defaults.
        return YAML.createObjectNode();
    }

    // --- helpers ---------------------------------------------------------------

    private static String str(JsonNode node, String field, String def) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return def;
        String s = v.asText();
        return s.isBlank() ? def : s;
    }

    private static int intVal(JsonNode node, String field, int def) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? def : v.asInt(def);
    }

    private static long longVal(JsonNode node, String field, long def) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? def : v.asLong(def);
    }

    private static String env(DotEnv dotenv, String key, String fallback) {
        String v = dotenv.get(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static int envInt(DotEnv dotenv, String key, int fallback) {
        return parseEnv(dotenv, key, fallback, Integer::parseInt);
    }

    private static long envLong(DotEnv dotenv, String key, long fallback) {
        return parseEnv(dotenv, key, fallback, Long::parseLong);
    }

    private static <T> T parseEnv(DotEnv dotenv, String key, T fallback, Function<String, T> parser) {
        String v = dotenv.get(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return parser.apply(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Env var " + key + " is not a valid number: " + v, e);
        }
    }

    /** Resolves a required value from env/.env first, then the YAML value; fails if both empty. */
    private static String require(DotEnv dotenv, String envKey, String yamlValue) {
        String v = dotenv.get(envKey);
        if (v != null && !v.isBlank()) return v;
        if (yamlValue != null && !yamlValue.isBlank() && !yamlValue.startsWith("YOUR_")) return yamlValue;
        throw new IllegalStateException(
                "Missing required config: set env " + envKey + " (or .env) or the corresponding config.yml value.");
    }
}
