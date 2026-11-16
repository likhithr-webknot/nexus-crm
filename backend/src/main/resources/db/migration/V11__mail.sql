CREATE TABLE mail_oauth_state (
  state_hash bytea PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id),
  expires_at timestamptz NOT NULL
);

CREATE TABLE mail_connection (
  user_id uuid PRIMARY KEY REFERENCES app_user(id),
  email citext NOT NULL,
  refresh_token_cipher bytea NOT NULL,
  connected_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE crm_module_record ADD COLUMN provider_message_id text;
ALTER TABLE crm_module_record ADD COLUMN sent_at timestamptz;
