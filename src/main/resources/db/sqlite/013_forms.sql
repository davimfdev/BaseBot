-- Configurable forms (BOTSPECS Module 1 — /formulario). A form has up to 5 questions
-- (Discord modal limit); submissions are routed to the #log-formularios channel.
CREATE TABLE IF NOT EXISTS forms (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    title      TEXT NOT NULL,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS form_questions (
    id       TEXT PRIMARY KEY,
    form_id  TEXT NOT NULL,
    position INTEGER NOT NULL,
    label    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_form_questions ON form_questions (form_id, position);
