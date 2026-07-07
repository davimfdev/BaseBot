package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.util.Emojis;

/**
 * The kinds of moderation case recorded in the infractions log. {@code name()} is the value
 * stored in the {@code type} column; {@link #label} / {@link #emoji} are for display.
 * Only {@link #WARN} counts toward auto-escalation; {@link #NOTE} is internal context.
 */
public enum InfractionType {

    WARN("Aviso", "" + Emojis.of(Emojis.WARN, "⚠️") + ""),
    NOTE("Nota", "" + Emojis.of(Emojis.NOTE, "📝") + ""),
    TIMEOUT("Timeout", "" + Emojis.of(Emojis.HOURGLASS, "⏳") + ""),
    MUTE("Mute", "" + Emojis.of(Emojis.MUTE, "🔇") + ""),
    MUTECALL("Mute de call", "" + Emojis.of(Emojis.VOLUME, "🔊") + ""),
    KICK("Expulsão", "" + Emojis.of(Emojis.KICK, "👢") + ""),
    BAN("Banimento", "" + Emojis.of(Emojis.BAN, "🔨") + ""),
    TEMPBAN("Ban temporário", "" + Emojis.of(Emojis.TIMER, "⏲️") + ""),
    SOFTBAN("Softban", "" + Emojis.of(Emojis.BROOM, "🧹") + "");

    private final String label;
    private final String emoji;

    InfractionType(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji;
    }

    /** Resolves a stored type string, falling back to {@link #NOTE}-style display for unknowns. */
    public static InfractionType fromKey(String key) {
        for (InfractionType t : values()) {
            if (t.name().equals(key)) {
                return t;
            }
        }
        return null;
    }
}
