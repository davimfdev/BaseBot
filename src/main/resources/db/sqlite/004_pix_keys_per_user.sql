-- Pix keys are per person (per user), not per role. Recreate pix_keys keyed by user_id.
-- The seller "vendedor" role (guild_config) only gates WHO may register/use Pix.
DROP TABLE IF EXISTS pix_keys;
CREATE TABLE IF NOT EXISTS pix_keys (
    guild_id      TEXT NOT NULL,
    user_id       TEXT NOT NULL,
    key_type      TEXT NOT NULL,   -- CPF | CNPJ | EMAIL | PHONE | RANDOM
    key_value     TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    merchant_city TEXT NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);
