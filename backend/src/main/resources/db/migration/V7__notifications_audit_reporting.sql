-- In-app notifications, the audit log, and what the dashboard reads.

CREATE TABLE notification (
  id            uuid PRIMARY KEY,
  recipient_id  uuid NOT NULL REFERENCES app_user(id),
  type          text NOT NULL,
  title         text NOT NULL,
  link          text,                              -- an in-app path only, for example /deals/{id}
  read_at       timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notification_unread ON notification (recipient_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX notification_created_at ON notification (created_at);   -- for the 90-day clean-up

-- Append-only: who changed what, and when.
CREATE TABLE audit_log (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  occurred_at  timestamptz NOT NULL DEFAULT now(),
  actor_id     uuid REFERENCES app_user(id),
  entity_type  text NOT NULL,
  entity_id    uuid NOT NULL,
  action       text NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','RESTORE','CONVERT')),
  changes      jsonb,                              -- {"field": [old, new]}
  request_id   text
);
CREATE INDEX audit_log_entity ON audit_log (entity_type, entity_id, occurred_at DESC);

-- Monthly revenue target. Always in the base currency (INR).
CREATE TABLE revenue_target (
  month       date PRIMARY KEY CHECK (extract(day FROM month) = 1),   -- the first day of the month
  amount      numeric(19,2) NOT NULL CHECK (amount >= 0),
  currency    char(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  updated_by  uuid REFERENCES app_user(id),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Nightly roll-up. It is rebuilt from scratch each night, so running it twice is safe.
CREATE TABLE daily_metric (
  day       date NOT NULL,
  owner_id  uuid NOT NULL REFERENCES app_user(id),
  metric    text NOT NULL,
  value     numeric(19,2) NOT NULL,
  PRIMARY KEY (day, owner_id, metric)
);
