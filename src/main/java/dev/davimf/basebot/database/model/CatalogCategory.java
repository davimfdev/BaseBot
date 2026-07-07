// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: CatalogCategory
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String name
//   - Record Component : public final int position
// [OUTLINE END]



package dev.davimf.basebot.database.model;

/** A product catalog category, scoped per guild (BOTSPECS Module 3 — /tabela). */
public record CatalogCategory(
        String id,
        String guildId,
        String name,
        int position
) {}
