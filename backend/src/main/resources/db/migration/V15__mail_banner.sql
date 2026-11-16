CREATE TABLE mail_banner (
  id int PRIMARY KEY CHECK (id = 1),
  image_png bytea NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid NOT NULL REFERENCES app_user(id)
);
