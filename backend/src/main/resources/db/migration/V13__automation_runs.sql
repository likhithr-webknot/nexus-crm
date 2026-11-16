CREATE TABLE automation_run (
  id uuid PRIMARY KEY,
  workflow_id uuid NOT NULL REFERENCES crm_module_record(id),
  event_key text NOT NULL,
  context jsonb NOT NULL,
  status text NOT NULL CHECK (status IN ('RUNNING','WAITING','COMPLETED','FAILED')),
  next_node text,
  due_at timestamptz,
  error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (workflow_id, event_key)
);
CREATE INDEX automation_ready ON automation_run (due_at) WHERE status = 'WAITING';
CREATE INDEX automation_history ON automation_run (workflow_id, created_at DESC);
