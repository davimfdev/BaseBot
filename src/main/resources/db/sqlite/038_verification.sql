-- Verificação (Base security). verified_members: quem já passou (drive do auto-cargo
-- na re-entrada, por servidor). verification_requests: fila de pendências, UMA por
-- pessoa (PK guild+user impede pedido duplicado / spam da fila).
CREATE TABLE IF NOT EXISTS verified_members (
    guild_id    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    verified_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);

CREATE TABLE IF NOT EXISTS verification_requests (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    message_id TEXT,
    answers    TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
