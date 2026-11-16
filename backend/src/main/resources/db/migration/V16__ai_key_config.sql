CREATE TABLE ai_key_config (
  id smallint PRIMARY KEY CHECK (id = 1),
  key_cipher bytea NOT NULL,
  key_name text NOT NULL,
  updated_by uuid NOT NULL REFERENCES app_user(id),
  updated_at timestamptz NOT NULL DEFAULT now()
);
