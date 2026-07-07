-- [OUTLINE START]
-- Created Tables:
-- - fac_recipes
-- - fac_recipe_inputs
-- Created Indices:
-- - idx_recipe_inputs
-- [OUTLINE END]



-- Production recipes (BOTSPECS Module 4 — /produzir). A recipe converts input items
-- (from fac_stock) into an output product (added back to fac_stock).
CREATE TABLE IF NOT EXISTS fac_recipes (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    product    TEXT NOT NULL,
    output_qty INTEGER NOT NULL DEFAULT 1,
    UNIQUE (guild_id, product)
);
CREATE TABLE IF NOT EXISTS fac_recipe_inputs (
    id        TEXT PRIMARY KEY,
    recipe_id TEXT NOT NULL,
    item      TEXT NOT NULL,
    qty       INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_recipe_inputs ON fac_recipe_inputs (recipe_id);
