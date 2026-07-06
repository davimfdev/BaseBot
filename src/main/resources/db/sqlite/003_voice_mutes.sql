-- [OUTLINE START]
-- Created Tables:
-- - voice_mutes
-- [OUTLINE END]



-- Persistent voice (call) mutes: re-applied on voice join. Guild-scoped, maps IDs.
CREATE TABLE IF NOT EXISTS voice_mutes (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
