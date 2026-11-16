CREATE TABLE crm_module_record (
  id uuid PRIMARY KEY,
  kind text NOT NULL CHECK (kind IN ('campaigns','products','quotes','communications','automations')),
  name text NOT NULL,
  status text NOT NULL,
  owner_id uuid NOT NULL REFERENCES app_user(id),
  account text NOT NULL DEFAULT '',
  description text NOT NULL DEFAULT '',
  amount numeric(18,2) NOT NULL DEFAULT 0,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  request_key text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  version int NOT NULL DEFAULT 0,
  UNIQUE (kind, owner_id, request_key)
);
CREATE INDEX crm_module_list ON crm_module_record (kind, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX crm_module_owner ON crm_module_record (owner_id, kind) WHERE deleted_at IS NULL;
CREATE INDEX crm_draft_expiry ON crm_module_record (created_at) WHERE kind = 'communications' AND status = 'Draft';
