package dev.davimf.basebot.modules.base.leveling;

import java.util.Locale;

/**
 * Formata durações de call: {@code 12h 34m 07s}, {@code 47m 03s}, {@code 52s}, {@code 0s}.
 *
 * <p>O zero-padding usa {@link Locale#ROOT}: sem isso, uma JVM iniciada num locale de dígitos
 * não-ASCII (por exemplo {@code ar-EG}) renderizaria outros glifos no meio da mensagem.
 */
public final class VoiceFormat {

    private VoiceFormat() {}

    public static String precise(long ms) {
        if (ms <= 0) {
            return "0s";
        }
        long totalSeconds = ms / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + String.format(Locale.ROOT, "%02dm %02ds", minutes, seconds);
        }
        if (minutes > 0) {
            return minutes + "m " + String.format(Locale.ROOT, "%02ds", seconds);
        }
        return seconds + "s";
    }
}
