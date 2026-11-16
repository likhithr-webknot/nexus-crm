CREATE TABLE saved_report (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id),
  name text NOT NULL,
  dataset text NOT NULL CHECK (dataset IN ('revenue','team','funnel','sources')),
  chart text NOT NULL CHECK (chart IN ('bar','line','pie')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX saved_report_owner ON saved_report (owner_id, created_at DESC);
