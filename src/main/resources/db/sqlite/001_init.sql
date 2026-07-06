-- [OUTLINE START]
-- Created Tables:
-- - active_tickets
-- - action_logs
-- - warnings
-- - budgets
-- - stock
-- - recruiter_stats
-- Created Indices:
-- - idx_active_tickets_guild
-- - idx_active_tickets_channel
-- - idx_action_logs_guild
-- - idx_warnings_expiry
-- - idx_warnings_user
-- - idx_budgets_expiry
-- [OUTLINE END]



-- BaseBot local SQLite schema (fast transactions / local state).
-- Forward-only. Append new migrations as new files; never edit this one after release.

-- ---------------------------------------------------------------------------
-- Module 2: Tickets — active tickets map Discord IDs (never names).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS active_tickets (
    id                TEXT PRIMARY KEY,            -- our ticket id (also used in transcript URL)
    guild_id          TEXT NOT NULL,
    text_channel_id   TEXT NOT NULL,
    voice_channel_id  TEXT,                        -- set when "Criar Call" is used
    creator_id        TEXT NOT NULL,
    assigned_staff_id TEXT,                        -- "Assumir Atendimento"
    suffix            TEXT,                        -- renameable suffix
    status            TEXT NOT NULL DEFAULT 'OPEN',-- OPEN | CLOSING | CLOSED
    created_at        TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_active_tickets_guild ON active_tickets (guild_id);
CREATE INDEX IF NOT EXISTS idx_active_tickets_channel ON active_tickets (text_channel_id);

-- ---------------------------------------------------------------------------
-- General + internal action logs (Module 1/2/4).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS action_logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   TEXT NOT NULL,
    actor_id   TEXT,                               -- who triggered it
    target_id  TEXT,                               -- affected user/channel/etc.
    action     TEXT NOT NULL,                      -- e.g. TICKET_NOTIFY, BAN, ADV_ADD
    detail     TEXT,                               -- free-form / JSON payload
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_action_logs_guild ON action_logs (guild_id, created_at);

-- ---------------------------------------------------------------------------
-- Module 4: ADV / Warns — stack & auto-expire in 20 days (scheduler + boot check).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS warnings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    level       INTEGER NOT NULL,                  -- ADV level (ADV 2 replaces ADV 1)
    reason      TEXT,
    issued_by   TEXT,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at  TEXT NOT NULL                      -- created_at + 20 days
);
CREATE INDEX IF NOT EXISTS idx_warnings_expiry ON warnings (expires_at);
CREATE INDEX IF NOT EXISTS idx_warnings_user ON warnings (guild_id, user_id);

-- ---------------------------------------------------------------------------
-- Module 3: Budgets (Orçamentos) — auto-cancel if pending > 24h (scheduler).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS budgets (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    seller_id   TEXT NOT NULL,
    client_id   TEXT NOT NULL,
    items_json  TEXT NOT NULL,                     -- [{product, qty, price}]
    total       REAL NOT NULL DEFAULT 0,
    status      TEXT NOT NULL DEFAULT 'PENDING',   -- PENDING | APPROVED | REJECTED | EXPIRED
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    expires_at  TEXT NOT NULL                      -- created_at + 24h
);
CREATE INDEX IF NOT EXISTS idx_budgets_expiry ON budgets (status, expires_at);

-- ---------------------------------------------------------------------------
-- Module 4: Stock (Farm/Produzir) & recruiter stats.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock (
    guild_id   TEXT NOT NULL,
    item       TEXT NOT NULL,
    quantity   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, item)
);

CREATE TABLE IF NOT EXISTS recruiter_stats (
    guild_id     TEXT NOT NULL,
    recruiter_id TEXT NOT NULL,
    recruits     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, recruiter_id)
);
