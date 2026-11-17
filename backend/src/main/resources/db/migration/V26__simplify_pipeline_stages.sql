-- The pipeline becomes Qualified -> In progress -> Won / Lost.
-- Demo, Proposal and Negotiation are folded into one "In progress" stage. Nothing is deleted while it is still in use:
-- deals, their stage history and saved views are moved first, and only then are the three old stages removed.

-- 1. The new stage. Its position is temporary (7) because positions must be unique; it is set properly in step 5.
INSERT INTO pipeline_stage (code, name, position, kind, win_probability)
VALUES ('IN_PROGRESS', 'In progress', 7, 'OPEN', 60);

-- 2. Deals in a retired stage move to In progress. Soft-deleted deals are moved too, so they restore into a stage that
--    exists. updated_at is left alone so lists do not reorder; version goes up so a person editing a deal at this
--    moment gets a conflict instead of silently saving the old stage back.
UPDATE deal
SET stage_code = 'IN_PROGRESS', version = version + 1
WHERE stage_code IN ('DEMO', 'PROPOSAL', 'NEGOTIATION');

-- 3. The history keeps every row, now pointing at the new stage. A move that happened between two retired stages
--    reads "In progress -> In progress"; the dates and who did it are kept.
UPDATE deal_stage_history SET from_stage = 'IN_PROGRESS' WHERE from_stage IN ('DEMO', 'PROPOSAL', 'NEGOTIATION');
UPDATE deal_stage_history SET to_stage = 'IN_PROGRESS' WHERE to_stage IN ('DEMO', 'PROPOSAL', 'NEGOTIATION');

-- 4. Saved deal views that filtered on a retired stage now filter on In progress.
UPDATE saved_view
SET filters = jsonb_set(filters, '{stage}', '"IN_PROGRESS"'), updated_at = now()
WHERE entity_type = 'deal' AND filters ->> 'stage' IN ('DEMO', 'PROPOSAL', 'NEGOTIATION');

-- 5. Nothing points at the old stages any more, so they can go. Then put the stages in order.
DELETE FROM pipeline_stage WHERE code IN ('DEMO', 'PROPOSAL', 'NEGOTIATION');
UPDATE pipeline_stage SET position = 2 WHERE code = 'IN_PROGRESS';
UPDATE pipeline_stage SET position = 3 WHERE code = 'WON';
UPDATE pipeline_stage SET position = 4 WHERE code = 'LOST';
