-- [OUTLINE START]
-- Created Tables:
-- - budgets
-- - budget_items
-- Created Indices:
-- - idx_budgets_status
-- - idx_budget_items_budget
-- [OUTLINE END]



-- Budgets / Orçamentos (BOTSPECS Module 3). A seller builds a budget for a client;
-- once sent it awaits client approval and auto-cancels after 24h (scheduler sweep).
CREATE TABLE IF NOT EXISTS budgets (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    seller_id  TEXT NOT NULL,
    client_id  TEXT NOT NULL,
    status     TEXT NOT NULL DEFAULT 'DRAFT',  -- DRAFT|PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED
    channel_id TEXT,
    message_id TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    sent_at    TEXT,
    expires_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_budgets_status ON budgets (status);

CREATE TABLE IF NOT EXISTS budget_items (
    id               TEXT PRIMARY KEY,
    budget_id        TEXT NOT NULL,
    product_name     TEXT NOT NULL,
    unit_price_cents INTEGER NOT NULL,
    quantity         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_budget_items_budget ON budget_items (budget_id);
