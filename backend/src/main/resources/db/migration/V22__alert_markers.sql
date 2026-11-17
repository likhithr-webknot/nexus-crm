-- When a stale-deal or overdue-follow-up alert was last sent for a row, so the hourly job tells people once.
-- A deal alerts again only after it saw new activity and went quiet again; a follow-up only after its due date moves.
ALTER TABLE deal ADD COLUMN stale_alerted_at timestamptz;
ALTER TABLE follow_up ADD COLUMN overdue_alerted_at timestamptz;

-- The job looks only at open deals and open follow-ups.
CREATE INDEX deal_open_for_alerts ON deal (created_at) WHERE deleted_at IS NULL AND closed_at IS NULL;
CREATE INDEX follow_up_open_due ON follow_up (due_at) WHERE deleted_at IS NULL AND status = 'OPEN';
