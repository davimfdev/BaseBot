// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: Punishment
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String userId
//   - Record Component : public final String type
//   - Record Component : public final int level
//   - Record Component : public final String reason
//   - Record Component : public final String appliedBy
//   - Record Component : public final String createdAt
//   - Record Component : public final String expiresAt
//   - Record Component : public final boolean active
// 
// Methods:
//   - `Method` : `public String label()`
// 
// Fields:
//   - `Field` : `public static final String BLACKLIST`
//   - `Field` : `public static final String DEMOTION`
//   - `Field` : `public static final String ADV`
// [OUTLINE END]



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
