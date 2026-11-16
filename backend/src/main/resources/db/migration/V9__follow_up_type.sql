-- What kind of follow-up it is. The calendar shows each kind in its own colour.
ALTER TABLE follow_up
  ADD COLUMN type text NOT NULL DEFAULT 'TASK' CHECK (type IN ('CALL','MEETING','EMAIL','TASK'));
