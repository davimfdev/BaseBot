-- Snapshots that each bot instance publishes so the dashboard can validate channels/roles
-- without a bot token. Written only by the bot; read by the dashboard. Idempotent.

CREATE TABLE IF NOT EXISTS bot_instances (
    id             UUID PRIMARY KEY,
    client_name    TEXT,
    bot_user_id    TEXT NOT NULL,
    application_id TEXT,
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bot_guilds (
    guild_id        TEXT PRIMARY KEY,
    bot_instance_id UUID NOT NULL REFERENCES bot_instances(id),
    guild_name      TEXT,
    owner_id        TEXT,
    bot_present     BOOLEAN NOT NULL DEFAULT true,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_bot_guilds_instance ON bot_guilds (bot_instance_id);

CREATE TABLE IF NOT EXISTS guild_channels_snapshot (
    guild_id     TEXT NOT NULL,
    channel_id   TEXT NOT NULL,
    name         TEXT,
    type         TEXT NOT NULL,
    parent_id    TEXT,
    position     INT,
    bot_can_view BOOLEAN NOT NULL DEFAULT false,
    bot_can_send BOOLEAN NOT NULL DEFAULT false,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, channel_id)
);

CREATE TABLE IF NOT EXISTS guild_roles_snapshot (
    guild_id       TEXT NOT NULL,
    role_id        TEXT NOT NULL,
    name           TEXT,
    position       INT,
    managed        BOOLEAN NOT NULL DEFAULT false,
    bot_can_assign BOOLEAN NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, role_id)
);
