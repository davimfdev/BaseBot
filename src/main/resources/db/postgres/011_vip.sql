-- Módulo Base: sistema de VIPs. Planos e grants são fonte-da-verdade compartilhada
-- com o dashboard (Neon). Idempotente (IF NOT EXISTS): seguro re-aplicar.

CREATE TABLE IF NOT EXISTS vip_plans (
    id                       TEXT PRIMARY KEY,
    guild_id                 TEXT NOT NULL,
    name                     TEXT NOT NULL,
    discord_category_id      TEXT,
    has_call                 BOOLEAN NOT NULL DEFAULT true,
    vip_role_id              TEXT,
    use_control_role         BOOLEAN NOT NULL DEFAULT true,
    xp_bonus_pct             INTEGER NOT NULL DEFAULT 0,
    eco_bonus_pct            INTEGER NOT NULL DEFAULT 0,
    default_duration_minutes BIGINT,
    reveal_default           BOOLEAN NOT NULL DEFAULT true,
    enabled                  BOOLEAN NOT NULL DEFAULT true,
    position                 INTEGER NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS vip_plans_guild_idx ON vip_plans (guild_id, position);
CREATE INDEX IF NOT EXISTS vip_plans_guild_enabled_idx ON vip_plans (guild_id, enabled, position);

CREATE TABLE IF NOT EXISTS vip_grants (
    id                  TEXT PRIMARY KEY,
    guild_id            TEXT NOT NULL,
    plan_id             TEXT NOT NULL REFERENCES vip_plans(id),
    user_id             TEXT NOT NULL,
    call_channel_id     TEXT,
    control_role_id     TEXT,
    reveal_on_occupancy BOOLEAN NOT NULL DEFAULT true,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    active              BOOLEAN NOT NULL DEFAULT true,
    provision_status    TEXT NOT NULL DEFAULT 'active',
    provision_error     TEXT,
    granted_by          TEXT,
    revoked_at          TIMESTAMPTZ,
    revoked_by          TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS vip_grants_one_active_per_user
    ON vip_grants (guild_id, user_id) WHERE active = true;
CREATE INDEX IF NOT EXISTS vip_grants_guild_active_idx ON vip_grants (guild_id, active);
CREATE INDEX IF NOT EXISTS vip_grants_plan_idx ON vip_grants (plan_id);
CREATE INDEX IF NOT EXISTS vip_grants_sweep_idx ON vip_grants (active, expires_at);
