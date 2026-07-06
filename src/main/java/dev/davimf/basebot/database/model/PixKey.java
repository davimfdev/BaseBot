// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: PixKey
// 
// Record Components:
//   - Record Component : public final String guildId
//   - Record Component : public final String userId
//   - Record Component : public final String keyType
//   - Record Component : public final String keyValue
//   - Record Component : public final String merchantName
//   - Record Component : public final String merchantCity
// [OUTLINE END]



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
