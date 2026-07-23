-- Solicitações de Set (recrutamento, BOTSPECS Módulo 4). Uma linha por mensagem de análise;
-- guarda as respostas do candidato para reexibir e montar o apelido no aceite.
CREATE TABLE IF NOT EXISTS recruit_request (
    message_id   TEXT PRIMARY KEY,
    guild_id     TEXT NOT NULL,
    applicant_id TEXT NOT NULL,
    recruiter_id TEXT NOT NULL,
    id_jogo      TEXT NOT NULL,
    nome         TEXT NOT NULL,
    telefone     TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
