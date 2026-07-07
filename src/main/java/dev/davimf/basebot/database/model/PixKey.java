package dev.davimf.basebot.database.model;

/** A seller's Pix key: multiple per guild+user (BOTSPECS Module 3). City is no longer stored. */
public record PixKey(
        long id,
        String guildId,
        String userId,
        String keyType,
        String keyValue,
        String merchantName
) {}
