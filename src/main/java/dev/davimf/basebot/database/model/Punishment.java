package dev.davimf.basebot.database.model;

/** A disciplinary record (BOTSPECS Module 4). ADV uses {@code level} + {@code expiresAt}. */
public record Punishment(
        String id,
        String guildId,
        String userId,
        String type,
        int level,
        String reason,
        String appliedBy,
        String createdAt,
        String expiresAt,
        boolean active
) {
    public static final String BLACKLIST = "BLACKLIST";
    public static final String DEMOTION = "DEMOTION";
    public static final String ADV = "ADV";

    /** Display label, e.g. {@code ADV 2}, {@code Blacklist}, {@code Rebaixamento}. */
    public String label() {
        return switch (type) {
            case ADV -> "ADV " + level;
            case BLACKLIST -> "Blacklist";
            case DEMOTION -> "Rebaixamento";
            default -> type;
        };
    }
}
