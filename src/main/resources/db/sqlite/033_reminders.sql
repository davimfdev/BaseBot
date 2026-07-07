CREATE TABLE IF NOT EXISTS reminders (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    message    TEXT NOT NULL,
    remind_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reminders_due ON reminders (remind_at);
