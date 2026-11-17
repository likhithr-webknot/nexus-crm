-- Calendar sync: a private subscription link per user (Apple Calendar, Outlook, any app that reads .ics).
-- Only a hash of the link's secret is stored, so a database leak does not leak working links.
CREATE TABLE calendar_feed_token (
  user_id uuid PRIMARY KEY REFERENCES app_user(id),
  token_hash bytea NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- One Google connection per user serves several features, each with its own consent. The scopes Google granted are
-- kept, so each feature knows whether it may run. Existing connections only ever had permission to send mail.
ALTER TABLE mail_oauth_state ADD COLUMN feature text NOT NULL DEFAULT 'mail' CHECK (feature IN ('mail','calendar','inbox'));
ALTER TABLE mail_connection ADD COLUMN scopes text NOT NULL DEFAULT 'https://www.googleapis.com/auth/gmail.send';
-- Set when Google says the stored permission was revoked or expired. The user must connect again.
ALTER TABLE mail_connection ADD COLUMN needs_reconnect boolean NOT NULL DEFAULT false;

-- Pushing follow-ups to the user's Google Calendar. A row here means sync is on for that user.
CREATE TABLE calendar_google_sync (
  user_id uuid PRIMARY KEY REFERENCES app_user(id),
  last_run_at timestamptz,
  last_synced_at timestamptz,
  last_error text
);

-- Which Google event shows which follow-up, and the follow-up version it was last written from.
-- No foreign key to follow_up: when a follow-up is purged the row stays so the event can be removed from Google.
CREATE TABLE calendar_link (
  follow_up_id uuid NOT NULL,
  user_id uuid NOT NULL REFERENCES app_user(id),
  google_event_id text NOT NULL,
  synced_version int NOT NULL,
  synced_at timestamptz NOT NULL DEFAULT now(),
  -- A reassigned follow-up has one link per person it was shown to, so each event can be removed.
  PRIMARY KEY (follow_up_id, user_id)
);
CREATE INDEX calendar_link_user ON calendar_link (user_id);
