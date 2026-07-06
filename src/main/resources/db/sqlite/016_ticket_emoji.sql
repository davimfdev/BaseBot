-- [OUTLINE START]
-- Table Alterations:
-- - active_tickets
-- [OUTLINE END]



-- Store the ticket category emoji so the channel name + dashboard header can keep it
-- after assume/rename (BOTSPECS Module 2 enhancement).
ALTER TABLE active_tickets ADD COLUMN emoji TEXT;
