package dev.davimf.basebot.database.model;

/** A seller's Pix key, scoped to a guild + role (BOTSPECS Module 3). */
public record PixKey(
        String guildId,
        String roleId,
        String keyType,
        String keyValue,
        String merchantName,
        String merchantCity
) {}
