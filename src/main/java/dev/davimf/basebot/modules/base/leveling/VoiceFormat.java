package dev.davimf.basebot.modules.base.leveling;

/** Formata durações de call para exibição: {@code 12h 34m}, {@code 34m}, {@code menos de 1m}. */
public final class VoiceFormat {

    private VoiceFormat() {}

    public static String duration(long ms) {
        if (ms <= 0) {
            return "0m";
        }
        long totalMinutes = ms / 60_000L;
        if (totalMinutes == 0) {
            return "menos de 1m";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours == 0 ? minutes + "m" : hours + "h " + String.format("%02dm", minutes);
    }
}
