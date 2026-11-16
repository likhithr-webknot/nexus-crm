ALTER TABLE company ADD COLUMN website_url text;
ALTER TABLE company ADD COLUMN logo_url text;

CREATE TABLE ai_research (
  kind text NOT NULL CHECK (kind IN ('company','contact')),
  record_id uuid NOT NULL,
  result jsonb NOT NULL,
  researched_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (kind, record_id)
);

CREATE TABLE ai_request (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_user(id),
  kind text NOT NULL,
  input_tokens int NOT NULL DEFAULT 0,
  output_tokens int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_request_user_recent ON ai_request (user_id, created_at DESC);

CREATE TABLE ai_alert (
  id uuid PRIMARY KEY,
  key_name text NOT NULL,
  alert_type text NOT NULL,
  period text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (key_name, alert_type, period)
);
