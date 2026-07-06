-- Richer action flow (BOTSPECS Module 4 — Ações): mark actions that already happened.
-- 1 = the action already happened (members are backfilled, no entry queue).
ALTER TABLE fac_actions ADD COLUMN is_past INTEGER NOT NULL DEFAULT 0;
