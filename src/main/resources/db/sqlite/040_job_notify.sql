CREATE TABLE IF NOT EXISTS eco_prefs (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    job_notify INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS eco_job_notify (
    guild_id    TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    action      TEXT NOT NULL,
    notified_ts INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id, action)
);
