-- [OUTLINE START]
-- Created Tables:
-- - pix_keys
-- [OUTLINE END]



-- Module 3: Pix keys registered per seller (identified by Discord role). Guild-scoped.
CREATE TABLE IF NOT EXISTS pix_keys (
    guild_id      TEXT NOT NULL,
    role_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,   -- CPF | CNPJ | EMAIL | PHONE | RANDOM
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    merchant_city TEXT NOT NULL,
    PRIMARY KEY (guild_id, role_id)
);
