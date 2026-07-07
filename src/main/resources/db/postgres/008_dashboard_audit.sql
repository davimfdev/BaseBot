CREATE TABLE IF NOT EXISTS dashboard_config_audit (
  id bigserial PRIMARY KEY,
  actor_user_id text NOT NULL,
  access_level text NOT NULL CHECK (access_level IN ('support', 'owner', 'delegate')),
  target_guild_id text NOT NULL,
  method text NOT NULL,
  route text NOT NULL,
  operation text NOT NULL,
  resource_type text NOT NULL,
  resource_id text,
  change_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  result text NOT NULL CHECK (result IN ('success', 'rejected', 'failed')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS dashboard_config_audit_guild_idx
  ON dashboard_config_audit(target_guild_id, created_at DESC);
