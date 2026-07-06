// [OUTLINE START]
// Package: dev.davimf.basebot.util
// 
// Class: Durations
// 
// Constructors:
//   - `Constructor` : `private Durations()`
// 
// Methods:
//   - `Method` : `private static final Pattern TOKEN = Pattern. compile()`
//   - `Method` : `private static final Pattern WHOLE = Pattern. compile()`
//   - `Method` : `public static OptionalLong parse(String input)`
//   - `Method` : `public static String format(long millis)`
//   - `Method` : `private static long unitMillis(char unit)`
// 
// Fields:
//   - `Field` : `public static final long MAX_MILLIS`
// [OUTLINE END]



package dev.davimf.basebot.util;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats short human durations like {@code 10m}, {@code 1h30m}, {@code 2d},
 * {@code 90s} (units s/m/h/d, optionally combined) into milliseconds. Used by timed
 * moderation (mute / mutecall).
 */
public final class Durations {

    /** Upper bound so a typo can't mute someone effectively forever. */
    public static final long MAX_MILLIS = 28L * 24 * 60 * 60 * 1000; // 28 days

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([smhd])");
    private static final Pattern WHOLE = Pattern.compile("^(\\s*\\d+\\s*[smhd]\\s*)+$");

    private Durations() {}

    /** Parses a duration to milliseconds (clamped to {@link #MAX_MILLIS}); empty if invalid. */
    public static OptionalLong parse(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        String s = input.trim().toLowerCase();
        if (s.isEmpty() || !WHOLE.matcher(s).matches()) {
            return OptionalLong.empty();
        }
        long total = 0;
        Matcher m = TOKEN.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += n * unitMillis(m.group(2).charAt(0));
            if (total > MAX_MILLIS) {
                return OptionalLong.of(MAX_MILLIS);
            }
        }
        return total > 0 ? OptionalLong.of(total) : OptionalLong.empty();
    }

    /** Compact human label, e.g. {@code 1d 2h 30m}. */
    public static String format(long millis) {
        long total = Math.max(0, millis);
        long d = total / 86_400_000;
        long h = (total % 86_400_000) / 3_600_000;
        long m = (total % 3_600_000) / 60_000;
        long sec = (total % 60_000) / 1000;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 || sb.isEmpty()) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    private static long unitMillis(char unit) {
        return switch (unit) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 0L;
        };
    }
}
