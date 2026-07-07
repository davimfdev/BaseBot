package dev.davimf.basebot.modules.base.moderation;

/**
 * One recorded moderation action (a "case"). {@code caseNumber} is per-guild sequential.
 * {@code expiresAt}/{@code durationMs} are null for actions without an expiry (note, kick,
 * permanent ban). {@code active} is cleared on revoke or expiry.
 */
public record Infraction(
        long id,
        String guildId,
        int caseNumber,
        String userId,
        String modId,
        String type,
        String reason,
        long createdAt,
        Long expiresAt,
        Long durationMs,
        boolean active) {

    /** The display metadata for this case's {@link #type}, or null if unknown. */
    public InfractionType kind() {
        return InfractionType.fromKey(type);
    }
}
