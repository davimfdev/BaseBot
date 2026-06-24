-- Pending farm submissions awaiting manager approval (BOTSPECS Module 4 — /farm).
-- Approval increments fac_stock and pays the farmer from the treasury (fac_finance).
CREATE TABLE IF NOT EXISTS fac_farm_pending (
    id         TEXT PRIMARY KEY,
    guild_id   TEXT NOT NULL,
    farmer_id  TEXT NOT NULL,
    item       TEXT NOT NULL,
    quantity   INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
