// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: FacTransaction
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String type
//   - Record Component : public final long amountCents
//   - Record Component : public final String actorId
//   - Record Component : public final String note
//   - Record Component : public final String createdAt
// [OUTLINE END]



package dev.davimf.basebot.database.model;

/** An audited treasury movement (BOTSPECS Module 4). {@code amountCents} is signed. */
public record FacTransaction(
        String id,
        String guildId,
        String type,
        long amountCents,
        String actorId,
        String note,
        String createdAt
) {}
