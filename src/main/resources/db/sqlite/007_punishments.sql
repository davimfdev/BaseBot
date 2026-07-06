-- [OUTLINE START]
-- Created Tables:
-- - punishments
-- Created Indices:
-- - idx_punishments_user
-- - idx_punishments_active
-- [OUTLINE END]



-- Punishments (BOTSPECS Module 4 — /punir, /punições). Blacklist, Demotion and ADV (warn).
-- ADV stacks (level N replaces N-1) and auto-expires after 20 days (scheduler + boot check).
CREATE TABLE IF NOT EXISTS punishments (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    type       TEXT NOT NULL,             -- BLACKLIST | DEMOTION | ADV
    level      INTEGER NOT NULL DEFAULT 0,-- ADV level (1,2,3...); 0 for others
    reason     TEXT,
    applied_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at TEXT,                       -- ADV only
    active     INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_punishments_user ON punishments (guild_id, user_id);
CREATE INDEX IF NOT EXISTS idx_punishments_active ON punishments (active);
