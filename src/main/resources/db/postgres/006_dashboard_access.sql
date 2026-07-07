-- Autorização do dashboard (owner-first + delegação): quem, além do dono, pode configurar
-- este servidor. Escrita SOMENTE pelo dashboard; o bot preserva (nunca lista no SET do save).
-- Forma: {"users": ["<user id>", ...], "roles": ["<role id>", ...]}
ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS dashboard_access JSONB NOT NULL DEFAULT '{}'::jsonb;
