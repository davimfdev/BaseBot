package dev.davimf.basebot.database.model;

/** A product catalog category, scoped per guild (BOTSPECS Module 3 — /tabela). */
public record CatalogCategory(
        String id,
        String guildId,
        String name,
        int position
) {}
