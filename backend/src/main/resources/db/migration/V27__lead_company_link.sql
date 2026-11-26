-- Leads point at a company record instead of only carrying free text.
-- company_name stays as the display/search copy of the linked company's name (and holds the text of
-- imported leads that match no company yet), so this migration is backward compatible.

ALTER TABLE lead ADD COLUMN company_id uuid REFERENCES company(id);
CREATE INDEX lead_company ON lead (company_id) WHERE company_id IS NOT NULL;

-- Link existing leads to a company when exactly one live company has the same name (case-insensitive).
UPDATE lead l
SET company_id = m.id
FROM (
  SELECT lower(name) AS lname, (array_agg(id))[1] AS id
  FROM company
  WHERE deleted_at IS NULL
  GROUP BY lower(name)
  HAVING count(*) = 1
) m
WHERE l.company_id IS NULL
  AND l.company_name IS NOT NULL
  AND lower(l.company_name) = m.lname;
