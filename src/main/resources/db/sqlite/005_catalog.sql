-- [OUTLINE START]
-- Created Tables:
-- - catalog_categories
-- - catalog_products
-- Created Indices:
-- - idx_catalog_categories_guild
-- - idx_catalog_products_cat
-- [OUTLINE END]



-- Product catalog (BOTSPECS Module 3 — /tabela). Categories -> Products, per guild.
CREATE TABLE IF NOT EXISTS catalog_categories (
    id        TEXT PRIMARY KEY,
    guild_id  TEXT NOT NULL,
    name      TEXT NOT NULL,
    position  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_catalog_categories_guild ON catalog_categories (guild_id);

CREATE TABLE IF NOT EXISTS catalog_products (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    category_id TEXT NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL DEFAULT 0,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_catalog_products_cat ON catalog_products (category_id);
