package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-guild list of farm/stock items, configured in {@code /setup → Farm} and stored
 * newline-joined in {@code guild_config.settings} under {@link #KEY}. Used by {@code /farm}
 * (delivery picker) and {@code /produzir} (recipe items) so the stock stays consistent —
 * members can only deliver/craft pre-configured items, never free text.
 */
public final class FarmItems {

    public static final String KEY = "farm-items";
    /** Discord caps a select menu at 25 options. */
    public static final int MAX = 25;

    private FarmItems() {}

    public static List<String> list(GuildConfig cfg) {
        String raw = cfg.setting(KEY);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) {
            String t = s.trim();
            if (!t.isEmpty() && out.stream().noneMatch(i -> i.equalsIgnoreCase(t))) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean contains(GuildConfig cfg, String item) {
        return item != null && list(cfg).stream().anyMatch(i -> i.equalsIgnoreCase(item.trim()));
    }

    /** The configured item matching {@code item} case-insensitively, or the trimmed input —
     *  so stock keys stay consistent regardless of typed casing. */
    public static String canonical(GuildConfig cfg, String item) {
        String t = item == null ? "" : item.trim();
        return list(cfg).stream().filter(i -> i.equalsIgnoreCase(t)).findFirst().orElse(t);
    }

    /** New newline-joined value with {@code item} appended (sanitized, deduped, capped). */
    public static String withAdded(GuildConfig cfg, String item) {
        String t = sanitize(item);
        List<String> items = new ArrayList<>(list(cfg));
        if (!t.isEmpty() && items.size() < MAX && items.stream().noneMatch(i -> i.equalsIgnoreCase(t))) {
            items.add(t);
        }
        return String.join("\n", items);
    }

    /** New newline-joined value with {@code item} removed. */
    public static String withRemoved(GuildConfig cfg, String item) {
        List<String> items = new ArrayList<>();
        for (String i : list(cfg)) {
            if (!i.equalsIgnoreCase(item.trim())) {
                items.add(i);
            }
        }
        return String.join("\n", items);
    }

    /** Item names: trimmed, no {@code :} (the ComponentId separator) or newlines, max 50 chars. */
    public static String sanitize(String item) {
        if (item == null) {
            return "";
        }
        String t = item.replace(":", "").replace("\n", " ").trim();
        return t.length() > 50 ? t.substring(0, 50).trim() : t;
    }
}
