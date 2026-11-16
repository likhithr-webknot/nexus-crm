-- Companies and contacts.
-- Every business table has the same audit columns: created_at, updated_at,
-- created_by, updated_by, version (optimistic locking) and deleted_at (soft delete).

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- fuzzy matching, used by search on the lead table

CREATE TABLE company (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,
  domain      citext,
  industry    text,
  owner_id    uuid NOT NULL REFERENCES app_user(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid REFERENCES app_user(id),
  updated_by  uuid REFERENCES app_user(id),
  version     int NOT NULL DEFAULT 0,
  deleted_at  timestamptz
);
CREATE UNIQUE INDEX company_domain_unique ON company (domain) WHERE deleted_at IS NULL AND domain IS NOT NULL;
CREATE INDEX company_owner ON company (owner_id) WHERE deleted_at IS NULL;

CREATE TABLE contact (
  id          uuid PRIMARY KEY,
  first_name  text NOT NULL,
  last_name   text NOT NULL,
  email       citext,
  phone       text,
  job_title   text,
  company_id  uuid REFERENCES company(id),
  owner_id    uuid NOT NULL REFERENCES app_user(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  created_by  uuid REFERENCES app_user(id),
  updated_by  uuid REFERENCES app_user(id),
  version     int NOT NULL DEFAULT 0,
  deleted_at  timestamptz
);
CREATE UNIQUE INDEX contact_email_unique ON contact (email) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX contact_company ON contact (company_id);
CREATE INDEX contact_owner ON contact (owner_id) WHERE deleted_at IS NULL;
