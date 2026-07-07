CREATE TABLE IF NOT EXISTS user_inventory (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    item_key   TEXT NOT NULL,
    slot       TEXT NOT NULL,
    usos_left  INTEGER NOT NULL,
    equipped   INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_inv_user_slot ON user_inventory(guild_id, user_id, slot);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_equipped_slot
    ON user_inventory(guild_id, user_id, slot) WHERE equipped = 1;

CREATE TABLE IF NOT EXISTS user_crime_state (
    guild_id   TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    preso_ate  INTEGER NOT NULL DEFAULT 0,
    ficha_suja INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
