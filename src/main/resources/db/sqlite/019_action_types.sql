-- Saved action types (BOTSPECS Module 4 — managed in /setup → Ações). Each type carries a
-- name, contingent bounds and a dirty-money payout, applied when an action is created.
CREATE TABLE IF NOT EXISTS fac_action_types (
    id             TEXT PRIMARY KEY,
    guild_id       TEXT NOT NULL,
    name           TEXT NOT NULL,
    max_contingent INTEGER NOT NULL DEFAULT 0,  -- 0 = unlimited capacity
    min_contingent INTEGER NOT NULL DEFAULT 0,  -- minimum confirmed to "align"
    dirty_money    INTEGER NOT NULL DEFAULT 0,  -- credited to dirty-money stock on Victory
    created_at     TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_action_types_guild_name
    ON fac_action_types (guild_id, name);

-- Denormalize the chosen type onto each action so history survives type edits/removal.
ALTER TABLE fac_actions ADD COLUMN action_name    TEXT;
ALTER TABLE fac_actions ADD COLUMN min_contingent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fac_actions ADD COLUMN dirty_money    INTEGER NOT NULL DEFAULT 0;
