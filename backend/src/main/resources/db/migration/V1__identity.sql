-- Identity: users, linked sign-in identities, refresh tokens and teams.
-- See docs/backend-technical-design.md, "Database schema".

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE team (
  id          uuid PRIMARY KEY,
  name        text NOT NULL UNIQUE,
  manager_id  uuid               -- FK added after app_user exists
);

CREATE TABLE app_user (
  id            uuid PRIMARY KEY,
  email         citext NOT NULL UNIQUE,
  display_name  text NOT NULL,
  avatar_url    text,
  role          text NOT NULL CHECK (role IN ('ADMIN','MANAGER','REP')),
  status        text NOT NULL CHECK (status IN ('INVITED','ACTIVE','DISABLED')),
  team_id       uuid REFERENCES team(id),
  invited_by    uuid REFERENCES app_user(id),
  last_login_at timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  version       int NOT NULL DEFAULT 0
);
ALTER TABLE team ADD FOREIGN KEY (manager_id) REFERENCES app_user(id);

CREATE TABLE user_identity (
  id             uuid PRIMARY KEY,
  user_id        uuid NOT NULL REFERENCES app_user(id),
  provider       text NOT NULL CHECK (provider IN ('GOOGLE')),
  subject        text NOT NULL,          -- the provider's stable 'sub' claim
  email_at_link  citext,
  linked_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provider, subject),
  -- An email is used to link an identity once. A second identity from the same
  -- provider would mean the email changed hands, so the database forbids it.
  UNIQUE (user_id, provider)
);

CREATE TABLE refresh_token (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES app_user(id),
  family_id    uuid NOT NULL,            -- one per sign-in; revoked together on reuse
  token_hash   bytea NOT NULL UNIQUE,    -- SHA-256 of the token, never the token itself
  issued_at    timestamptz NOT NULL,
  expires_at   timestamptz NOT NULL,
  used_at      timestamptz,
  revoked_at   timestamptz,
  user_agent   text,
  ip           inet
);
CREATE INDEX ON refresh_token (user_id);
CREATE INDEX ON refresh_token (family_id);
