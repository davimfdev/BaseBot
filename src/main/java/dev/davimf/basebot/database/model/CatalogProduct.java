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
