CREATE TABLE IF NOT EXISTS user_wallets (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    cash     INTEGER NOT NULL DEFAULT 0,
    bank     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
CREATE TABLE IF NOT EXISTS eco_cooldowns (
    guild_id TEXT NOT NULL,
    user_id  TEXT NOT NULL,
    action   TEXT NOT NULL,
    last_ts  INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id, action)
);
