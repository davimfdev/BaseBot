package dev.davimf.basebot.database.model;

/** A raw-material stock row for a faction (BOTSPECS Module 4). */
public record StockEntry(String guildId, String item, long quantity) {}
