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
