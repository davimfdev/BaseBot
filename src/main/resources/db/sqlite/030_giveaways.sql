CREATE TABLE IF NOT EXISTS giveaways (
    id                  TEXT PRIMARY KEY,
    guild_id            TEXT NOT NULL,
    channel_id          TEXT NOT NULL,
    message_id          TEXT,
    prize               TEXT NOT NULL,
    coin_reward         INTEGER NOT NULL DEFAULT 0,
    winners             INTEGER NOT NULL DEFAULT 1,
    ends_at             INTEGER NOT NULL,
    ended               INTEGER NOT NULL DEFAULT 0,
    req_role_id         TEXT,
    req_min_days        INTEGER NOT NULL DEFAULT 0,
    req_min_voice_hours INTEGER NOT NULL DEFAULT 0,
    req_window_start    INTEGER NOT NULL DEFAULT -1,
    req_window_end      INTEGER NOT NULL DEFAULT -1
);
CREATE TABLE IF NOT EXISTS giveaway_entries (
    giveaway_id TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    PRIMARY KEY (giveaway_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_giveaways_active ON giveaways (ended, ends_at);
