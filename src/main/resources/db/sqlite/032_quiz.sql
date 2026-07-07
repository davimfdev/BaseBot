CREATE TABLE IF NOT EXISTS quiz_questions (
    id       TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    question TEXT NOT NULL,
    correct  TEXT NOT NULL,
    wrong1   TEXT NOT NULL,
    wrong2   TEXT NOT NULL,
    wrong3   TEXT NOT NULL
);
