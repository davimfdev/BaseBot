CREATE TABLE IF NOT EXISTS voice_sessions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id          TEXT NOT NULL,
    user_id           TEXT NOT NULL,
    channel_id        TEXT NOT NULL,
    join_time         INTEGER NOT NULL,
    leave_time        INTEGER,
    xp_credited_until INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_voice_sessions_open ON voice_sessions (guild_id, user_id) WHERE leave_time IS NULL;
