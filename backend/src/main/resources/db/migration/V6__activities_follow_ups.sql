-- Timeline entries and follow-up tasks. Both attach to a lead, a contact or a deal.
-- At least one of the three must be set.

CREATE TABLE activity (
  id           uuid PRIMARY KEY,
  type         text NOT NULL CHECK (type IN ('NOTE','CALL','MEETING','EMAIL_LOG','SYSTEM')),
  subject      text NOT NULL,
  body         text,                              -- plain text; the frontend shows it escaped
  occurred_at  timestamptz NOT NULL,
  actor_id     uuid REFERENCES app_user(id),      -- NULL for system entries
  lead_id      uuid REFERENCES lead(id),
  contact_id   uuid REFERENCES contact(id),
  deal_id      uuid REFERENCES deal(id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  created_by   uuid REFERENCES app_user(id),
  updated_by   uuid REFERENCES app_user(id),
  version      int NOT NULL DEFAULT 0,
  deleted_at   timestamptz,
  CHECK (num_nonnulls(lead_id, contact_id, deal_id) >= 1)
);
CREATE INDEX activity_lead ON activity (lead_id, occurred_at DESC) WHERE lead_id IS NOT NULL;
CREATE INDEX activity_contact ON activity (contact_id, occurred_at DESC) WHERE contact_id IS NOT NULL;
CREATE INDEX activity_deal ON activity (deal_id, occurred_at DESC) WHERE deal_id IS NOT NULL;

CREATE TABLE follow_up (
  id            uuid PRIMARY KEY,
  title         text NOT NULL,
  notes         text,
  due_at        timestamptz NOT NULL,
  assignee_id   uuid NOT NULL REFERENCES app_user(id),
  status        text NOT NULL CHECK (status IN ('OPEN','DONE','CANCELLED')),
  completed_at  timestamptz,
  reminded_at   timestamptz,                      -- set once the reminder has been sent
  lead_id       uuid REFERENCES lead(id),
  contact_id    uuid REFERENCES contact(id),
  deal_id       uuid REFERENCES deal(id),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  created_by    uuid REFERENCES app_user(id),
  updated_by    uuid REFERENCES app_user(id),
  version       int NOT NULL DEFAULT 0,
  deleted_at    timestamptz,
  CHECK (num_nonnulls(lead_id, contact_id, deal_id) >= 1),
  CHECK (status <> 'DONE' OR completed_at IS NOT NULL)
);
CREATE INDEX follow_up_assignee ON follow_up (assignee_id, status, due_at);
-- For the reminder job.
CREATE INDEX follow_up_to_remind ON follow_up (due_at) WHERE status = 'OPEN' AND reminded_at IS NULL;
CREATE INDEX follow_up_lead ON follow_up (lead_id) WHERE lead_id IS NOT NULL;
CREATE INDEX follow_up_contact ON follow_up (contact_id) WHERE contact_id IS NOT NULL;
CREATE INDEX follow_up_deal ON follow_up (deal_id) WHERE deal_id IS NOT NULL;
