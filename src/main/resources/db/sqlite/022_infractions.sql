-- Moderation infractions / case log (Base moderation system, design 2026-06-29).
-- One row per moderation action (warn/note/timeout/mute/mutecall/kick/ban/tempban/softban).
-- case_number is per-guild sequential; active drives escalation counting and "in effect".
CREATE TABLE IF NOT EXISTS infractions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT    NOT NULL,
    case_number INTEGER NOT NULL,
    user_id     TEXT    NOT NULL,
    mod_id      TEXT    NOT NULL,
    type        TEXT    NOT NULL,
    reason      TEXT,
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER,
    duration_ms INTEGER,
    active      INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_infractions_guild_user ON infractions (guild_id, user_id);
CREATE INDEX IF NOT EXISTS idx_infractions_guild_case ON infractions (guild_id, case_number);
CREATE INDEX IF NOT EXISTS idx_infractions_expiry ON infractions (expires_at);
