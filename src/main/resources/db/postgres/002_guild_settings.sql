-- Free-form text settings per guild (ticket description/emoji, percentages, etc.).
ALTER TABLE guild_config
    ADD COLUMN IF NOT EXISTS settings JSONB NOT NULL DEFAULT '{}'::jsonb;
