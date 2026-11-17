-- Inbound email sync: mail to or from a known contact or lead is copied (subject, people, a short snippet) onto that record.
-- Mail that matches nobody is never stored.

-- A row means inbox sync is on for that user.
CREATE TABLE mail_sync_state (
  user_id uuid PRIMARY KEY REFERENCES app_user(id),
  -- Gmail's marker for "everything up to here has been read". Null until the first backfill has started.
  history_id text,
  -- While the last 30 days are still being read, where to continue.
  backfill_token text,
  backfilling boolean NOT NULL DEFAULT true,
  last_run_at timestamptz,
  last_synced_at timestamptz,
  last_error text
);

-- One row per message per record it matched, so a message to two contacts shows on both.
CREATE TABLE email_message (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id),
  provider_message_id text NOT NULL,
  thread_id text,
  -- The Message-ID header, the same in every mailbox that holds the message. Used to show a message once.
  rfc_message_id text,
  direction text NOT NULL CHECK (direction IN ('IN','OUT')),
  from_addr text NOT NULL,
  to_addrs text NOT NULL,
  subject text NOT NULL,
  snippet text,
  sent_at timestamptz NOT NULL,
  contact_id uuid REFERENCES contact(id),
  lead_id uuid REFERENCES lead(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (num_nonnulls(contact_id, lead_id) = 1)
);
CREATE UNIQUE INDEX email_message_contact_unique ON email_message (user_id, provider_message_id, contact_id) WHERE contact_id IS NOT NULL;
CREATE UNIQUE INDEX email_message_lead_unique ON email_message (user_id, provider_message_id, lead_id) WHERE lead_id IS NOT NULL;
CREATE INDEX email_message_contact ON email_message (contact_id, sent_at DESC) WHERE contact_id IS NOT NULL;
CREATE INDEX email_message_lead ON email_message (lead_id, sent_at DESC) WHERE lead_id IS NOT NULL;
CREATE INDEX email_message_user ON email_message (user_id);
