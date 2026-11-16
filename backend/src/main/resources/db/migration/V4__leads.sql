-- Leads, their status history, and CSV import jobs.

CREATE TABLE lead (
  id                    uuid PRIMARY KEY,
  first_name            text NOT NULL,
  last_name             text NOT NULL,
  email                 citext,
  phone                 text,
  company_name          text,
  job_title             text,
  source                text NOT NULL CHECK (source IN ('WEBSITE','SOCIAL','REFERRAL','CAMPAIGN','MANUAL','IMPORT')),
  status                text NOT NULL CHECK (status IN ('NEW','CONTACTED','QUALIFIED','DISQUALIFIED','CONVERTED')),
  owner_id              uuid REFERENCES app_user(id),           -- NULL means unassigned
  estimated_value       numeric(19,4) CHECK (estimated_value >= 0),
  currency              char(3) NOT NULL DEFAULT 'INR' REFERENCES currency(code),
  disqualify_reason     text,
  converted_at          timestamptz,
  converted_company_id  uuid REFERENCES company(id),
  converted_contact_id  uuid REFERENCES contact(id),
  converted_deal_id     uuid,                                   -- foreign key added in V5, once deal exists
  -- Lower-case text for fuzzy search. The database fills it in; the app never writes it.
  search_text           text GENERATED ALWAYS AS (
                          lower(first_name || ' ' || last_name || ' ' ||
                                coalesce(email::text, '') || ' ' || coalesce(company_name, ''))) STORED,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  created_by            uuid REFERENCES app_user(id),
  updated_by            uuid REFERENCES app_user(id),
  version               int NOT NULL DEFAULT 0,
  deleted_at            timestamptz,
  CHECK (status <> 'CONVERTED'    OR converted_deal_id IS NOT NULL),
  CHECK (status <> 'DISQUALIFIED' OR disqualify_reason IS NOT NULL)
);
-- One open lead per email. Converted or disqualified leads may repeat an email.
CREATE UNIQUE INDEX lead_open_email_unique ON lead (email)
  WHERE deleted_at IS NULL AND status IN ('NEW','CONTACTED','QUALIFIED');
CREATE INDEX lead_owner_status ON lead (owner_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX lead_created_at ON lead (created_at);
CREATE INDEX lead_search_text ON lead USING gin (search_text gin_trgm_ops);

-- Append-only: rows are added, never changed.
CREATE TABLE lead_status_history (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  lead_id      uuid NOT NULL REFERENCES lead(id),
  from_status  text,                      -- NULL for the first entry
  to_status    text NOT NULL,
  changed_at   timestamptz NOT NULL DEFAULT now(),
  changed_by   uuid REFERENCES app_user(id)
);
CREATE INDEX lead_status_history_lead ON lead_status_history (lead_id);
CREATE INDEX lead_status_history_to_status ON lead_status_history (to_status, changed_at);

CREATE TABLE import_job (
  id             uuid PRIMARY KEY,
  uploaded_by    uuid NOT NULL REFERENCES app_user(id),
  status         text NOT NULL CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
  total_rows     int,
  imported_rows  int,
  failed_rows    int,
  error_report   jsonb,                   -- row number and reason, for download
  created_at     timestamptz NOT NULL DEFAULT now(),
  finished_at    timestamptz
);
