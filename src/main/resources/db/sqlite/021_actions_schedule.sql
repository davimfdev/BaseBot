-- Action entry lock + schedule (BOTSPECS Module 4 — Ações).
-- entries_open: 0 = the Entrar button is locked.
ALTER TABLE fac_actions ADD COLUMN entries_open INTEGER NOT NULL DEFAULT 1;
-- due_at: scheduled time as epoch millis (0 = none / past action).
ALTER TABLE fac_actions ADD COLUMN due_at INTEGER NOT NULL DEFAULT 0;
-- due_notified: 1 = the scheduler already revealed Vitória/Derrota for this action.
ALTER TABLE fac_actions ADD COLUMN due_notified INTEGER NOT NULL DEFAULT 0;
