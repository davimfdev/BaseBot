-- [OUTLINE START]
-- Created Tables:
-- - fac_finance
-- - fac_transactions
-- - fac_stock
-- Created Indices:
-- - idx_fac_tx_guild
-- [OUTLINE END]



-- Faction economy (BOTSPECS Module 4): treasury balance, an audit log of movements,
-- and raw-material stock. Balance + stock are guild-scoped.
CREATE TABLE IF NOT EXISTS fac_finance (
    guild_id      TEXT PRIMARY KEY,
    balance_cents INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS fac_transactions (
    id           TEXT PRIMARY KEY,
    guild_id     TEXT NOT NULL,
    type         TEXT NOT NULL,        -- DEPOSIT|WITHDRAW|TRANSFER|SALE|FARM_PAYOUT
    amount_cents INTEGER NOT NULL,     -- signed: +credit / -debit
    actor_id     TEXT,
    note         TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_fac_tx_guild ON fac_transactions (guild_id, created_at);

CREATE TABLE IF NOT EXISTS fac_stock (
    guild_id TEXT NOT NULL,
    item     TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, item)
);
