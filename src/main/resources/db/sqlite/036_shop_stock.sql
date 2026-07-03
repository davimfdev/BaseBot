-- Operational sold-counter for shop items. Config (price/limits/name) lives in Postgres
-- (shop_items); this stays in SQLite so Neon never takes an operational write per purchase.
CREATE TABLE IF NOT EXISTS shop_stock (
    item_id INTEGER PRIMARY KEY,
    sold    INTEGER NOT NULL DEFAULT 0
);
