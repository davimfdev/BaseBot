CREATE TABLE IF NOT EXISTS shop_items (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT NOT NULL,
    type        TEXT NOT NULL,
    role_id     TEXT,
    name        TEXT NOT NULL,
    description TEXT,
    price       INTEGER NOT NULL,
    duration_s  INTEGER,
    stock       INTEGER,
    per_user    INTEGER,
    sold        INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS shop_purchases (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id    TEXT NOT NULL,
    item_id     INTEGER NOT NULL,
    user_id     TEXT NOT NULL,
    role_id     TEXT,
    expires_at  INTEGER,
    price_paid  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_shop_purch_expiry ON shop_purchases(expires_at) WHERE expires_at IS NOT NULL;
