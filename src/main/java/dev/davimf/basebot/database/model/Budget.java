package dev.davimf.basebot.database.model;

/** A sales budget (orçamento) a seller builds for a client (BOTSPECS Module 3). */
public record Budget(
        String id,
        String guildId,
        String sellerId,
        String clientId,
        String status,
        String channelId,
        String messageId,
        String createdAt,
        String sentAt,
        String expiresAt
) {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";
}
