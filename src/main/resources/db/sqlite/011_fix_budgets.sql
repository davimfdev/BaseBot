-- Fix: 001_init.sql shipped a placeholder `budgets` table (items_json/total) that
-- predates the real implementation, so 006's CREATE TABLE IF NOT EXISTS was a no-op on
-- existing DBs and BudgetRepository's columns (channel_id/message_id/sent_at) were
-- missing. Rebuild both budget tables to the schema the code expects. Budgets are
-- ephemeral, so dropping the placeholder rows is safe.
DROP TABLE IF EXISTS budget_items;
DROP TABLE IF EXISTS budgets;

CREATE TABLE budgets (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    seller_id  TEXT NOT NULL,
    client_id  TEXT NOT NULL,
    status     TEXT NOT NULL DEFAULT 'DRAFT',
    channel_id TEXT,
    message_id TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    sent_at    TEXT,
    expires_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_budgets_status ON budgets (status);

CREATE TABLE budget_items (
    id               TEXT PRIMARY KEY,
    budget_id        TEXT NOT NULL,
    product_name     TEXT NOT NULL,
    unit_price_cents INTEGER NOT NULL,
    quantity         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_budget_items_budget ON budget_items (budget_id);
