-- BaseBot Postgres schema for Guild Configuration (source of truth).
--
-- Run manually against the Postgres/Neon database. The bot reads/writes this via
-- JdbcGuildConfigRepository. JSONB columns absorb the long tail of dashboard
-- settings so adding a feature toggle/channel mapping needs no migration.
--
-- NOTE: The web dashboard currently persists guild config in Supabase (`guilds`
-- table). When that store is migrated to a dedicated Neon schema, reconcile the
-- column mapping here with the dashboard so both sides read/write the same rows.

CREATE TABLE IF NOT EXISTS guild_config (
    guild_id              TEXT PRIMARY KEY,
    log_channel_id        TEXT,
    ticket_log_channel_id TEXT,
    channels              JSONB NOT NULL DEFAULT '{}'::jsonb,  -- logical name -> channel id
    roles                 JSONB NOT NULL DEFAULT '{}'::jsonb,  -- logical name -> role id
    toggles               JSONB NOT NULL DEFAULT '{}'::jsonb,  -- feature flag -> bool
    staff_role_ids        JSONB NOT NULL DEFAULT '[]'::jsonb,  -- ticket-allowed staff roles
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
