-- Tempo semanal em call, espelhado do SQLite pelo VoiceTimeFlusher para o dashboard ler.
-- Idempotente (IF NOT EXISTS): seguro re-aplicar.

CREATE TABLE IF NOT EXISTS voice_weekly_time (
    guild_id   TEXT   NOT NULL,
    user_id    TEXT   NOT NULL,
    week_start BIGINT NOT NULL,
    ms         BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (guild_id, user_id, week_start)
);
CREATE INDEX IF NOT EXISTS idx_voice_weekly_time_rank
    ON voice_weekly_time (guild_id, week_start, ms DESC);
