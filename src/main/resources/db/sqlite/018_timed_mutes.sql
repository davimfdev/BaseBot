-- [OUTLINE START]
-- Created Tables:
-- - timed_mutes
-- Created Indices:
-- - idx_timed_mutes_expiry
-- [OUTLINE END]



-- Timed mutes (BOTSPECS Module 1). A mute (TEXT = "mutado" role, VOICE = server mute)
-- lasts until expires_at_millis, then a scheduler sweep removes it. Replaces voice_mutes.
CREATE TABLE IF NOT EXISTS timed_mutes (
    guild_id          TEXT NOT NULL,
    user_id           TEXT NOT NULL,
    type              TEXT NOT NULL,        -- TEXT | VOICE
    expires_at_millis INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id, type)
);
CREATE INDEX IF NOT EXISTS idx_timed_mutes_expiry ON timed_mutes (expires_at_millis);
