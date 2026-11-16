-- Pipeline stages, deals and their stage history.

CREATE TABLE pipeline_stage (
  code             text PRIMARY KEY,
  name             text NOT NULL,
  position         smallint NOT NULL UNIQUE,    -- left-to-right order on the board
  kind             text NOT NULL CHECK (kind IN ('OPEN','WON','LOST')),
  win_probability  smallint NOT NULL CHECK (win_probability BETWEEN 0 AND 100)
);

-- The one fixed pipeline. Probabilities match the frontend's forecast.
INSERT INTO pipeline_stage (code, name, position, kind, win_probability) VALUES
  ('QUALIFIED',   'Qualified',   1, 'OPEN',  40),
  ('DEMO',        'Demo',        2, 'OPEN',  55),
  ('PROPOSAL',    'Proposal',    3, 'OPEN',  70),
  ('NEGOTIATION', 'Negotiation', 4, 'OPEN',  85),
  ('WON',         'Won',         5, 'WON',  100),
  ('LOST',        'Lost',        6, 'LOST',   0);

CREATE TABLE deal (
  id                   uuid PRIMARY KEY,
  name                 text NOT NULL,
  company_id           uuid NOT NULL REFERENCES company(id),
  primary_contact_id   uuid REFERENCES contact(id),
  lead_id              uuid REFERENCES lead(id),
  owner_id             uuid NOT NULL REFERENCES app_user(id),
  stage_code           text NOT NULL REFERENCES pipeline_stage(code),
  amount               numeric(19,4) NOT NULL CHECK (amount >= 0),
  currency             char(3) NOT NULL DEFAULT 'INR' REFERENCES currency(code),
  source               text,                      -- copied from the lead, for reports
  expected_close_date  date,
  closed_at            timestamptz,
  lost_reason          text,
  -- Set when the deal is won or lost: the INR value at that day's rate. It never changes afterwards.
  base_amount          numeric(19,4),
  fx_rate              numeric(18,8),
  fx_rate_date         date,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  created_by           uuid REFERENCES app_user(id),
  updated_by           uuid REFERENCES app_user(id),
  version              int NOT NULL DEFAULT 0,
  deleted_at           timestamptz,
  CHECK (stage_code <> 'LOST' OR lost_reason IS NOT NULL)
);
ALTER TABLE lead ADD CONSTRAINT lead_converted_deal_fk FOREIGN KEY (converted_deal_id) REFERENCES deal(id);
CREATE INDEX deal_stage_owner ON deal (stage_code, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX deal_owner ON deal (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX deal_closed_at ON deal (closed_at) WHERE closed_at IS NOT NULL;
CREATE INDEX deal_company ON deal (company_id);
CREATE INDEX deal_lead ON deal (lead_id) WHERE lead_id IS NOT NULL;

-- Append-only: rows are added, never changed.
CREATE TABLE deal_stage_history (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  deal_id           uuid NOT NULL REFERENCES deal(id),
  from_stage        text REFERENCES pipeline_stage(code),   -- NULL for the first entry
  to_stage          text NOT NULL REFERENCES pipeline_stage(code),
  amount_at_change  numeric(19,4) NOT NULL,
  currency          char(3) NOT NULL REFERENCES currency(code),
  changed_at        timestamptz NOT NULL DEFAULT now(),
  changed_by        uuid REFERENCES app_user(id)
);
CREATE INDEX deal_stage_history_deal ON deal_stage_history (deal_id);
CREATE INDEX deal_stage_history_to_stage ON deal_stage_history (to_stage, changed_at);
