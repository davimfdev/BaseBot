// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: CatalogProduct
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String categoryId
//   - Record Component : public final String name
//   - Record Component : public final String description
//   - Record Component : public final long priceCents
//   - Record Component : public final int position
// [OUTLINE END]



package dev.davimf.basebot.database.model;

/** A catalog product belonging to a {@link CatalogCategory}. Price is stored in cents. */
public record CatalogProduct(
        String id,
        String guildId,
        String categoryId,
        String name,
        String description,
        long priceCents,
        int position
) {}
