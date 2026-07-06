// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: StockEntry
// 
// Record Components:
//   - Record Component : public final String guildId
//   - Record Component : public final String item
//   - Record Component : public final long quantity
// [OUTLINE END]



package dev.davimf.basebot.database.model;

/** A raw-material stock row for a faction (BOTSPECS Module 4). */
public record StockEntry(String guildId, String item, long quantity) {}
