// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: EmbedColor
// 
// Constructors:
//   - `Constructor` : `private EmbedColor()`
// 
// Methods:
//   - `Method` : `public static OptionalInt parse(String hex)`
//   - `Method` : `public static int resolve(GuildConfig cfg)`
//   - `Method` : `public static String hex(int color)`
// 
// Fields:
//   - `Field` : `public static final int DEFAULT`
//   - `Field` : `public static final String SETTING_KEY`
// [OUTLINE END]



package dev.davimf.basebot.util;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.OptionalInt;

/**
 * Per-guild accent color for every embed/Components V2 container the bot sends. Stored
 * as a hex string in {@code guild_config.settings} and configured via {@code /setup → Bot}.
 */
public final class EmbedColor {

    /** Default accent (Discord blurple) when the guild hasn't set one. */
    public static final int DEFAULT = 0x5865F2;

    /** Key under {@code guild_config.settings} holding the hex color. */
    public static final String SETTING_KEY = "embed-color";

    private EmbedColor() {}

    /** Parses a hex color ({@code #RGB}, {@code #RRGGBB}, with optional {@code #}/{@code 0x}). */
    public static OptionalInt parse(String hex) {
        if (hex == null) {
            return OptionalInt.empty();
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        } else if (h.startsWith("0x") || h.startsWith("0X")) {
            h = h.substring(2);
        }
        if (h.length() == 3 && h.matches("[0-9a-fA-F]{3}")) {
            StringBuilder sb = new StringBuilder();
            for (char c : h.toCharArray()) {
                sb.append(c).append(c);
            }
            h = sb.toString();
        }
        if (!h.matches("[0-9a-fA-F]{6}")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(h, 16));
    }

    /** The guild's configured color, or {@link #DEFAULT} when unset/invalid. */
    public static int resolve(GuildConfig cfg) {
        return parse(cfg.setting(SETTING_KEY)).orElse(DEFAULT);
    }

    /** Formats a color int as {@code #RRGGBB}. */
    public static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }
}
