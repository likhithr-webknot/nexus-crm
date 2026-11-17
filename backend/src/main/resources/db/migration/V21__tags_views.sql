-- Tags on leads, contacts, companies and deals, and saved list views.

CREATE TABLE tag (
  id uuid PRIMARY KEY,
  name citext NOT NULL UNIQUE,
  color text NOT NULL DEFAULT 'neutral' CHECK (color IN ('neutral','brand','violet','green','amber','red')),
  created_by uuid REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- No foreign key on entity_id: it points at one of four tables. The nightly purge deletes these rows with the record.
CREATE TABLE entity_tag (
  entity_type text NOT NULL CHECK (entity_type IN ('lead','contact','company','deal')),
  entity_id uuid NOT NULL,
  tag_id uuid NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
  PRIMARY KEY (entity_type, entity_id, tag_id)
);
CREATE INDEX entity_tag_tag ON entity_tag (tag_id, entity_type);

CREATE TABLE saved_view (
  id uuid PRIMARY KEY,
  owner_id uuid NOT NULL REFERENCES app_user(id),
  entity_type text NOT NULL CHECK (entity_type IN ('lead','contact','deal')),
  name text NOT NULL,
  filters jsonb NOT NULL DEFAULT '{}'::jsonb,
  shared boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX saved_view_owner ON saved_view (owner_id, entity_type);
CREATE INDEX saved_view_shared ON saved_view (entity_type) WHERE shared;
