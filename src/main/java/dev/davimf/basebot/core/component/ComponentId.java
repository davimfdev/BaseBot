package dev.davimf.basebot.core.component;

import java.util.Arrays;

/**
 * Parsed custom-id of the form {@code namespace:action:arg0:arg1:...}.
 *
 * <p>Centralizing the encoding keeps every component's custom-id consistent and under
 * Discord's 100-character limit. Use {@link #of(String, String, String...)} when
 * building components and the parsed fields when handling them.
 */
public record ComponentId(String namespace, String action, String[] args, String raw) {

    public static final String SEP = ":";

    /** Builds a custom-id string: {@code namespace:action:arg0:arg1...}. */
    public static String of(String namespace, String action, String... args) {
        StringBuilder sb = new StringBuilder(namespace).append(SEP).append(action);
        for (String a : args) {
            sb.append(SEP).append(a == null ? "" : a);
        }
        String id = sb.toString();
        if (id.length() > 100) {
            throw new IllegalArgumentException("custom-id exceeds Discord's 100-char limit: " + id);
        }
        return id;
    }

    /** Parses a raw custom-id. Missing action becomes "" and args is empty. */
    public static ComponentId parse(String raw) {
        String[] parts = raw.split(SEP, -1);
        String namespace = parts.length > 0 ? parts[0] : "";
        String action = parts.length > 1 ? parts[1] : "";
        String[] args = parts.length > 2 ? Arrays.copyOfRange(parts, 2, parts.length) : new String[0];
        return new ComponentId(namespace, action, args, raw);
    }

    /** Returns arg at {@code index}, or null if absent. */
    public String arg(int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }
}
