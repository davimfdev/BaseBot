-- [OUTLINE START]
-- Created Tables:
-- - message_drafts
-- [OUTLINE END]



-- Interactive message builder drafts (BOTSPECS Module 1 — /mensagem). One in-progress
-- draft per user; the whole builder state is a JSON blob so blocks/fields can evolve.
CREATE TABLE IF NOT EXISTS message_drafts (
    user_id    TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    json       TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
