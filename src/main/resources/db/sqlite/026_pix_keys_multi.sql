CREATE TABLE pix_keys_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id      TEXT NOT NULL,
    user_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
INSERT INTO pix_keys_new (guild_id, user_id, key_type, key_value, merchant_name)
    SELECT guild_id, user_id, key_type, key_value, merchant_name FROM pix_keys;
DROP TABLE pix_keys;
ALTER TABLE pix_keys_new RENAME TO pix_keys;
CREATE INDEX idx_pix_keys_user ON pix_keys (guild_id, user_id);
