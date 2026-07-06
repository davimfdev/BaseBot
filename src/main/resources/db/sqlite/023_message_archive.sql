CREATE TABLE IF NOT EXISTS message_archive (
    message_id TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    channel_id TEXT NOT NULL,
    author_id  TEXT NOT NULL,
    content    TEXT,
    attachments TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_archive_created
    ON message_archive (created_at);

CREATE INDEX IF NOT EXISTS idx_message_archive_guild_channel
    ON message_archive (guild_id, channel_id);
