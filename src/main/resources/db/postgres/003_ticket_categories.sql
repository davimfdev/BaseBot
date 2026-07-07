-- [OUTLINE START]
-- Created Tables:
-- - ticket_categories
-- Created Indices:
-- - ticket_categories_guild_idx
-- [OUTLINE END]



-- Module 2: ticket categories (types). A guild can have many; each one drives a button
-- on the ticket panel and the channel it opens. Config is source-of-truth in Postgres.
CREATE TABLE IF NOT EXISTS ticket_categories (
    id                  TEXT PRIMARY KEY,
    guild_id            TEXT NOT NULL,
    name                TEXT NOT NULL,
    emoji               TEXT,                          -- channel-name-safe unicode emoji (nullable)
    description         TEXT,
    discord_category_id TEXT NOT NULL,                 -- Discord parent category where channels open
    staff_role_ids      JSONB NOT NULL DEFAULT '[]'::jsonb,
    position            INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ticket_categories_guild_idx ON ticket_categories (guild_id, position);
