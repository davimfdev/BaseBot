-- Perguntas de verificação (Base · Segurança). Uma linha por pergunta, até 5 por guild,
-- ordenadas por position. Editadas pelo /setup e futuramente pelo dashboard; o bot lê
-- para montar o modal. Aplicar MANUALMENTE no Neon. Idempotente.
CREATE TABLE IF NOT EXISTS verification_questions (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    prompt      TEXT NOT NULL,
    required    BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_verification_questions_guild
    ON verification_questions (guild_id);
