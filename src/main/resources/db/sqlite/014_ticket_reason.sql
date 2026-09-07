-- Store the reason the member gave when opening a ticket (asked via modal at creation),
-- shown in the first message for the staff (BOTSPECS Module 2 enhancement).
ALTER TABLE active_tickets ADD COLUMN reason TEXT;
