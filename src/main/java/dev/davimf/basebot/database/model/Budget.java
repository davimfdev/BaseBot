// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: Budget
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String sellerId
//   - Record Component : public final String clientId
//   - Record Component : public final String status
//   - Record Component : public final String channelId
//   - Record Component : public final String messageId
//   - Record Component : public final String createdAt
//   - Record Component : public final String sentAt
//   - Record Component : public final String expiresAt
// 
// Fields:
//   - `Field` : `public static final String DRAFT`
//   - `Field` : `public static final String PENDING`
//   - `Field` : `public static final String APPROVED`
//   - `Field` : `public static final String REJECTED`
//   - `Field` : `public static final String EXPIRED`
//   - `Field` : `public static final String CANCELLED`
// [OUTLINE END]



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
