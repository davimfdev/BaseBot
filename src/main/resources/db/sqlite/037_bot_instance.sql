-- Bot-local durable key/value. Holds the stable bot instance id generated on first boot
-- (key='bot_instance_id'), so the operator doesn't have to set BOT_INSTANCE_ID manually.
-- Survives restarts with the SQLite file; a wiped local DB is effectively a new instance.
CREATE TABLE IF NOT EXISTS local_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
