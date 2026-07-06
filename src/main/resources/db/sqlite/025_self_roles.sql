CREATE TABLE IF NOT EXISTS self_role_panels (
    id            TEXT PRIMARY KEY,
    guild_id      TEXT NOT NULL,
    title         TEXT NOT NULL,
    description   TEXT,
    style         TEXT NOT NULL DEFAULT 'buttons',
    unique_choice INTEGER NOT NULL DEFAULT 0,
    channel_id    TEXT,
    message_id    TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS self_role_options (
    panel_id TEXT NOT NULL,
    role_id  TEXT NOT NULL,
    label    TEXT NOT NULL,
    emoji    TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (panel_id, role_id)
);
