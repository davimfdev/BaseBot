-- [OUTLINE START]
-- Created Tables:
-- - ticket_events
-- Created Indices:
-- - idx_ticket_events
-- [OUTLINE END]



-- Persisted ticket action notices (open, assume, call, member, notify, rename) so the
-- transcript always contains them, even when the MESSAGE_CONTENT intent is off and the
-- bot can't read embeds back from channel history (BOTSPECS Module 2).
CREATE TABLE IF NOT EXISTS ticket_events (
    id                TEXT PRIMARY KEY,
    ticket_id         TEXT NOT NULL,
    text              TEXT NOT NULL,
    created_at_millis INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ticket_events ON ticket_events (ticket_id, created_at_millis);
