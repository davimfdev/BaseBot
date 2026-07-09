ALTER TABLE voice_sessions ADD COLUMN time_credited_until INTEGER NOT NULL DEFAULT 0;
UPDATE voice_sessions SET time_credited_until = xp_credited_until WHERE time_credited_until = 0;
CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT    NOT NULL,
    user_id    TEXT    NOT NULL,
    week_start INTEGER NOT NULL,
    ms         INTEGER NOT NULL DEFAULT 0,
    dirty      INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_rank ON voice_weekly_time (guild_id, week_start, ms DESC);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_dirty ON voice_weekly_time (dirty) WHERE dirty = 1;
