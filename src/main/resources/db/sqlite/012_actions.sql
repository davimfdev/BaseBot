-- [OUTLINE START]
-- Created Tables:
-- - fac_actions
-- - fac_action_participants
-- Created Indices:
-- - idx_action_participants
-- [OUTLINE END]



-- Actions / Reservations (BOTSPECS Module 4 — /painel-acoes). An action has a capacity;
-- members confirm or land on a reserve list, with Elite priority over normal members.
CREATE TABLE IF NOT EXISTS fac_actions (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    channel_id TEXT,
    message_id TEXT,
    when_text  TEXT,
    capacity   INTEGER NOT NULL DEFAULT 0,   -- 0 = unlimited
    status     TEXT NOT NULL DEFAULT 'OPEN',  -- OPEN | CLOSED | VICTORY | DEFEAT
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS fac_action_participants (
    action_id TEXT NOT NULL,
    user_id   TEXT NOT NULL,
    kind      TEXT NOT NULL,                  -- CONFIRMED | RESERVE
    priority  INTEGER NOT NULL DEFAULT 0,     -- 1 if Elite at join time
    joined_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (action_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_action_participants ON fac_action_participants (action_id, kind);
