-- Auditoria de quem editou a config pelo dashboard. Escrita SOMENTE pelo dashboard (Parte B);
-- o bot não a preenche (usa updated_at). Nullable: linhas antigas/editadas pelo bot ficam NULL.
ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS updated_by TEXT;
