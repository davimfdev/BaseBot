package dev.davimf.basebot.database.model;

/** A seller's Pix key, scoped to a guild + the seller's user id (BOTSPECS Module 3). */
public record PixKey(
        String guildId,
        String userId,
        String keyType,
        String keyValue,
        String merchantName,
        String merchantCity
) {}
