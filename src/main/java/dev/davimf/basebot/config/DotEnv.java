package dev.davimf.basebot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal {@code .env} loader. Parses {@code KEY=VALUE} lines (ignoring blanks and
 * {@code #} comments, tolerating an {@code export} prefix and surrounding quotes) and
 * exposes a lookup where the real process environment always takes precedence over the
 * file — so production can rely purely on env vars while local dev uses {@code .env}.
 */
public final class DotEnv {

    private final Map<String, String> values;

    private DotEnv(Map<String, String> values) {
        this.values = values;
    }

    public static DotEnv load(Path file) {
        if (Files.exists(file)) {
            try {
                return new DotEnv(parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read .env file: " + file, e);
            }
        }
        return new DotEnv(Map.of());
    }

    /** Process env wins; otherwise the parsed {@code .env} value; otherwise null. */
    public String get(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        return values.get(key);
    }

    public static Map<String, String> parse(String content) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : content.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripQuotes(line.substring(eq + 1).strip());
            out.put(key, value);
        }
        return out;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
