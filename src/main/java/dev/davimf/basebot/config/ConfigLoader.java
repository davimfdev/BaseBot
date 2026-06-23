package dev.davimf.basebot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Loads {@link BotConfig} from a YAML file, then overlays environment variables.
 *
 * <p>Precedence (highest first): environment variable -&gt; YAML value -&gt; built-in default.
 * This lets secrets stay out of the file in production (env only) while keeping a
 * convenient local {@code config.yml}.
 */
public final class ConfigLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {}

    /** Loads from {@code config.yml} in the working directory if present. */
    public static BotConfig load() {
        return load(Path.of("config.yml"));
    }

    public static BotConfig load(Path file) {
        JsonNode root = readYamlOrEmpty(file);

        JsonNode discord = root.path("discord");
        JsonNode postgres = root.path("postgres");
        JsonNode sqlite = root.path("sqlite");
        JsonNode tickets = root.path("tickets");
        JsonNode rl = root.path("ratelimit");

        return new BotConfig(
                new BotConfig.Discord(
                        require("BOT_TOKEN", str(discord, "token", null)),
                        env("DEV_GUILD_ID", str(discord, "devGuildId", null))
                ),
                new BotConfig.Postgres(
                        env("POSTGRES_URL", str(postgres, "url", "jdbc:postgresql://localhost:5432/basebot")),
                        env("POSTGRES_USER", str(postgres, "username", "basebot")),
                        env("POSTGRES_PASSWORD", str(postgres, "password", "")),
                        envInt("POSTGRES_MAX_POOL", intVal(postgres, "maxPoolSize", 5)),
                        env("POSTGRES_SCHEMA", str(postgres, "schema", "public"))
                ),
                new BotConfig.Sqlite(
                        env("SQLITE_PATH", str(sqlite, "path", "data/basebot.db"))
                ),
                new BotConfig.Tickets(
                        env("TICKET_INGEST_URL", str(tickets, "ingestUrl", "https://davimf.dev/api/ticket-store")),
                        env("TICKET_INGEST_SECRET", str(tickets, "ingestSecret", "")),
                        env("TICKET_VIEW_BASE", str(tickets, "viewBaseUrl", "https://davimf.dev/ticket")),
                        envInt("TICKET_PBKDF2_ITERATIONS", intVal(tickets, "pbkdf2Iterations", 210_000)),
                        env("TICKET_SOURCE", str(tickets, "source", "basebot"))
                ),
                new BotConfig.RateLimit(
                        envLong("EMBED_DEBOUNCE_MS", longVal(rl, "embedDebounceMillis", 5_000L)),
                        envInt("GHOST_PING_BATCH", intVal(rl, "ghostPingBatchSize", 5)),
                        envInt("GHOST_PING_INTERVAL_S", intVal(rl, "ghostPingBatchIntervalSeconds", 30))
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
        // No file: rely entirely on env vars + defaults.
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

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static int envInt(String key, int fallback) {
        return parseEnv(key, fallback, Integer::parseInt);
    }

    private static long envLong(String key, long fallback) {
        return parseEnv(key, fallback, Long::parseLong);
    }

    private static <T> T parseEnv(String key, T fallback, Function<String, T> parser) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return parser.apply(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Env var " + key + " is not a valid number: " + v, e);
        }
    }

    /** Resolves a required value from env first, then the YAML value; fails if both empty. */
    private static String require(String envKey, String yamlValue) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        if (yamlValue != null && !yamlValue.isBlank() && !yamlValue.startsWith("YOUR_")) return yamlValue;
        throw new IllegalStateException(
                "Missing required config: set env " + envKey + " or the corresponding config.yml value.");
    }
}
