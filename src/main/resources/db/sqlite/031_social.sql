CREATE TABLE IF NOT EXISTS social_points (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    type     TEXT NOT NULL,
    points   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id, type)
);
CREATE TABLE IF NOT EXISTS social_gifts (
    guild_id TEXT NOT NULL,
    giver_id TEXT NOT NULL,
    type     TEXT NOT NULL,
    last_ts  INTEGER NOT NULL,
    PRIMARY KEY (guild_id, giver_id, type)
);
